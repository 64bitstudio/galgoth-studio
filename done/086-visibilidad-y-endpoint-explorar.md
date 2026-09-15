# 086 — Cambiar visibilidad de un proyecto + endpoint de Explorar

## Objetivo
Ver `docs/definiciones/proyectos-por-usuario-y-explorar.md` (VoBo de
Marco recibido) — HU-4 y HU-5. Con ownership real (`084`) y enforcement
(`085`) ya en pie, falta la pieza que le da valor de producto a todo
esto: que el dueño pueda publicar un proyecto, y que exista una API de
lectura pública para listarlos.

**Depende de:** `084` y `085`.

## Alcance
- **Sí incluye:**
  - `PATCH /api/projects/{projectId}/visibility` (o el shape que se
    decida en implementación — detalle de API, no de producto): cambia
    `PRIVATE ↔ PUBLIC` de un proyecto propio. No-dueño → `404` (mismo
    criterio de "Riesgos y preguntas abiertas" del documento: no se
    distingue "existe pero no es tuyo" de "no existe").
  - `GET /api/explore/projects` (nuevo, `permitAll()`): lista proyectos
    `visibility = PUBLIC`, orden por `updated_at desc` — mismo orden que
    "Mis proyectos" hoy. Sin búsqueda/filtros/paginación en esta primera
    pasada (ver "No incluye" del documento).
  - `projects.owner_display_name` (columna nueva, migración `V6`):
    capturado del `nombre`/`apellidos` de la sesión del frontend al
    crear el proyecto (decisión recomendada del documento, aprobada en
    el VoBo) — se envía en `CreateProjectRequest`, se graba tal cual, sin
    resolverlo después contra auth-core-mc.
  - Detalle de solo lectura de un proyecto público ajeno: extiende
    `ProjectController.get` (ya cubierto por `requireViewable` del
    ticket `085`) para incluir `ownerDisplayName` y la lista de mobs
    (nombre + miniatura + estado) en la respuesta — sin acciones de
    edición en el payload (el frontend decide qué mostrar, pero el
    contrato ya distingue "es mío" vs "ajeno público" en la respuesta).
- **No incluye:** visor 3D en modo lectura, duplicar un proyecto ajeno,
  moderación, perfiles públicos — todo explícitamente fuera de alcance
  en el documento de definición.

## Criterios de aceptación (TDD)
- Dueño cambia la visibilidad de su proyecto → aparece/desaparece de
  `GET /api/explore/projects` de inmediato.
- No-dueño intenta cambiar visibilidad de un proyecto ajeno → `404`.
- `GET /api/explore/projects` sin `Authorization` funciona (anónimo) y
  nunca incluye un proyecto `PRIVATE`.
- `owner_display_name` se graba al crear y viaja en la respuesta de
  Explorar y del detalle público.
- Suite completa del backend en verde.
- Verificación en vivo contra DEV: publicar un proyecto real, confirmar
  que aparece en `GET /api/explore/projects`, despublicarlo y confirmar
  que desaparece.

## Hecho

**PR:** [#125](https://github.com/64bitstudio/galgoth-studio/pull/125), mergeado a `dev` (`6f3f09a`). Deploy real a DEV verificado en verde (build 124 de Jenkins).

**Implementado tal cual el alcance, con una simplificación documentada:**
- `PATCH /api/projects/{projectId}/visibility` — solo dueño (`ProjectAccessGuard.requireOwner`, ticket 085), valida `PRIVATE`/`PUBLIC` (`InvalidVisibilityException` → 400 `INVALID_VISIBILITY` si no, sin mutar nada), 404 `PROJECT_NOT_FOUND` si no es dueño o no existe (nunca distingue los dos casos).
- `GET /api/explore/projects` — nuevo `ExploreProjectController`, `permitAll()` en `SecurityConfig`, lista proyectos `visibility = PUBLIC` y no borrados vía `ProjectRepository.findByVisibilityAndDeletedAtIsNullOrderByUpdatedAtDesc`, orden `updated_at desc`. Reutiliza `ProjectSummary` tal cual (mismo shape que "Mis proyectos") — sin DTO nuevo.
- `projects.owner_display_name` (migración `V7`, no `V6` como decía el borrador original del ticket — `V6` ya la tomó el ticket 091 en el orden real de ejecución) — se llena en `CreateProjectRequest.ownerDisplayName`, el frontend (`projectsApi.ts`) lo arma desde `useSessionStore().user` (`"${nombre} ${apellidos}"`, o `null` sin sesión), se graba tal cual sin resolverlo después contra auth-core-mc.
- **Simplificación de alcance (documentada en `docs/API.md`, no silenciosa):** el criterio de aceptación de "extender `ProjectController.get` con la lista de mobs para el detalle público" no se implementó como un cambio en `ProjectDetail` — resultó innecesario porque el ticket 085 ya expone `GET /api/projects/{id}/mobs` públicamente para proyectos `PUBLIC` a través del guard existente (`requireViewable`). Añadir la lista inline hubiera duplicado esa misma información en dos contratos distintos sin necesidad real.

**Hallazgos reales en el camino (todos resueltos, ninguno bajado de estándar en silencio):**
1. **CI de PR falló dos veces antes de quedar verde**, ambas diagnosticadas leyendo el log real de Jenkins vía SSH (nunca asumido en verde por "pasó en mi máquina"):
   - Build 1: `vue-tsc -b` (modo build real de CI) falló con TS2322 en 5 fixtures de test (`AiMobProjectPickerDialog.spec.ts`, `HomeView.spec.ts`, `ProjectCard.spec.ts`, `ProjectsDashboard.spec.ts`, `RecentProjectCard.spec.ts`) que armaban un `ProjectSummary` vía `Partial<T>` sin incluir el nuevo campo requerido `ownerDisplayName` — localmente había verificado solo con `vue-tsc --noEmit`, que no detecta este caso. **Hallazgo de proceso:** guardado como memoria persistente (`vue-tsc-build-vs-noemit-mismatch`) — de ahora en adelante, verificar con `npm run build` real, no solo `--noEmit`.
   - Build 2: Quality Gate de SonarQube en rojo en ambos proyectos (frontend y backend), diagnosticado vía SSH+Postgres directo (técnica ya memorizada, `sonarqube-quality-gate-diagnosis`) confirmando `islast` sobre el commit real de esta rama (sin colisión de otra rama):
     - Backend, `S1192` CRITICAL real y nuevo: `ProjectService.changeVisibility`/`listPublic` duplicaban los literales `"PRIVATE"`/`"PUBLIC"` en vez de usar la constante ya existente — se agregó la constante `PUBLIC` que faltaba.
     - Frontend, `S1135` (recurrencia ya conocida, memoria `sonar-s1135-todo-false-positive`): un comentario preexistente en `authApi.ts` (archivo NO tocado por este ticket, pero bloqueando el gate de esta rama por la palabra "todo") se reformuló sin cambiar su significado.
2. **`GALGOTH_INTERNAL_SECRET`** (gap de infra del ticket 091) ya estaba resuelto en DEV antes de este ticket — el deploy real de este ticket no lo volvió a tocar, confirmado en el build 124.

**Verificación en vivo contra DEV real (no simulada):**
- Cuenta de prueba real registrada/logueada contra `auth-dev.64bitstudio.com` (`X-Client-Id: galgoth-studio`), token real usado contra `studio-dev.galgoth.64bitstudio.com`.
- Proyecto creado con `ownerDisplayName` explícito → nace `PRIVATE`, confirmado ausente de `GET /api/explore/projects`.
- Publicado (`PATCH .../visibility {"PUBLIC"}`) → aparece en Explorar **sin sesión** (curl sin `Authorization`), con `ownerDisplayName` correcto.
- Despublicado (`{"PRIVATE"}`) → desaparece de Explorar de inmediato.
- `PATCH .../visibility` sin `Authorization` → `401`.
- `PATCH .../visibility` con `visibility: "HIDDEN"` → `400 INVALID_VISIBILITY`, sin mutar el proyecto.
- Segunda cuenta de prueba intenta publicar el proyecto de la primera → `404 PROJECT_NOT_FOUND` (nunca `403`, nunca revela existencia).
- Limpieza posterior: proyecto y ambas cuentas de prueba borrados a mano vía SQL directo en las bases de DEV (`galgoth-studio-dev-postgres-1`, `auth-core-mc-dev-postgres-1`, respetando FKs de `refresh_token`/`login_event`).

**Tests:** 6 nuevos en `ProjectControllerTest` + `ExploreProjectControllerTest` nuevo (4 tests, Testcontainers+MockMvc de punta a punta) + `projectsApi.spec.ts` actualizado. 483 tests backend + 766 tests frontend en verde, `npm run build` (`vue-tsc -b` real) limpio.

**Docs:** `docs/API.md` (sección nueva + nota de la simplificación de alcance), `docs/BASE_DE_DATOS.md` (V7), colección Postman.
