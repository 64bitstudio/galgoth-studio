# 085 — Enforcement de acceso a proyectos (dueño / público / privado)

## Objetivo
Ver `docs/definiciones/proyectos-por-usuario-y-explorar.md` (VoBo de
Marco recibido) — HU-3, cierre real del ticket `077` ("queda listo para
que un ticket futuro decida qué rutas proteger" — este es ese ticket).
Con `owner_ref`/`visibility` ya reales (ticket `084`), las 14+ rutas de
proyectos/mobs/drafts/texturas/export/geometría siguen completamente
abiertas: cualquiera con el ID puede editar o borrar el proyecto de
otro.

**Depende de:** `084` (necesita `owner_ref`/`visibility` reales para
tener algo contra qué comparar).

## Alcance
- **Sí incluye:**
  - `ProjectAccessGuard` nuevo (ver "Diseño técnico → 3" del documento de
    definición): `requireOwner(projectId, callerId)` para mutaciones,
    `requireViewable(projectId, callerId)` para lecturas (dueño real O
    `visibility = PUBLIC`, si no `ProjectNotFoundException` → `404`).
  - Los 9 controladores anidados bajo `/api/projects/{projectId}/**`
    (`MobController`, `MobDetailController`, `MobDraftController`,
    `MobExportController`, `MobGeometryController`,
    `MobReferenceImageController`, `MobTextureController`,
    `MobThumbnailController`, y `ProjectController` mismo) pasan por el
    guard antes de operar.
  - `SecurityConfig`: reemplaza `anyRequest().permitAll()` por la tabla
    de reglas explícita de la sección "Diseño técnico → 4" del
    documento — mutaciones y "Mis proyectos"/"recientes" exigen
    `authenticated()`; lecturas de detalle quedan `permitAll()` a nivel
    de Spring Security (la decisión real la toma el guard, para permitir
    lectura anónima de un proyecto público).
  - Intentar `GET`/`PATCH`/`DELETE` un proyecto privado ajeno (o
    cualquier recurso anidado suyo) responde `404`, nunca `403` — no
    revela que el proyecto existe.
- **No incluye:** el endpoint de cambio de visibilidad ni Explorar
  (ticket `086`), nada de frontend.

## Criterios de aceptación (TDD)
- Test explícito: dueño real accede sin problema a cada operación
  (mutación y lectura) de su propio proyecto — sin regresión.
- Test explícito: un usuario distinto al dueño recibe `404` al intentar
  mutar un proyecto ajeno (privado o público), y al leer uno privado
  ajeno.
- Test explícito: una lectura sin `Authorization` de un proyecto
  `PUBLIC` funciona (200); la misma lectura sin `Authorization` de uno
  `PRIVATE` responde `404`.
- Suite completa del backend en verde, incluyendo los tests existentes
  (confirma que no se rompió ningún flujo de dueño real).
- Verificación en vivo contra DEV: dos cuentas de prueba reales, se
  confirma que la cuenta B no puede ver/editar un proyecto privado de la
  cuenta A por URL directa.

## Hecho
- `ProjectAccessGuard` (`project.access`, nuevo): `requireOwner`/`requireViewable`, siempre `ProjectNotFoundException` (`404`) cuando el caller no tiene acceso, nunca `403`.
- Los 9 controladores en alcance (`ProjectController`, `MobController`, `MobDetailController`, `MobDraftController`, `MobExportController`, `MobGeometryController`, `MobReferenceImageController`, `MobTextureController`, `MobThumbnailController`) resuelven el `projectId` real (directo, o vía `mob.projectId`) y llaman al guard antes de operar.
- `SecurityConfig`: reemplaza `anyRequest().permitAll()` por reglas explícitas -- mutaciones exigen `authenticated()`, lecturas quedan `permitAll()` (el guard decide dueño/público). `ApiAuthenticationEntryPoint` nuevo para que un `401` de Spring (rechazado antes de llegar al controlador) tenga la misma forma `ApiErrorResponse` que el resto de la API.

### Hallazgo real, cambio de alcance con VoBo de Marco (no estaba en el plan original)
Al implementar el guard sobre los 3 endpoints que sirven bytes crudos de una imagen (`GET .../thumbnail`, `GET .../texture`, `GET .../references/{id}`), encontré que el frontend los renderiza vía `<img src="...">` directo (dashboard, tarjetas de proyecto/mob, pantalla de exportación, wizard de referencia) -- un `<img>` del navegador nunca puede mandar `Authorization: Bearer`. Protegerlos como el resto habría roto el thumbnail/textura de **cualquier** proyecto privado para su propio dueño en cuanto se desplegara (todos los proyectos nacen `PRIVATE` desde el ticket 084) -- una regresión mucho más amplia que la del hotfix 089. Se lo planteé a Marco con 3 opciones explícitas; decisión (recomendada): esos 3 endpoints quedan **deliberadamente sin enforcement** (bytes servidos por id no adivinable, mismo criterio de confianza que una URL firmada), documentado en `docs/API.md` como límite conocido hasta un ticket de seguimiento que reescriba la carga de imágenes del frontend (fetch autenticado + blob URL). Todo lo demás (CRUD de proyectos/mobs, drafts, revisiones, geometría, subida de textura/referencia/thumbnail, y el listado JSON de referencias) sí quedó protegido tal como estaba previsto.

### "Nada de frontend" -- pull-forward necesario, mismo criterio que el hotfix 089
El ticket decía explícitamente "No incluye: ... nada de frontend", pero al agregar `authenticated()` a las mutaciones (y a la lectura vía guard de recursos antes completamente abiertos) descubrí que 6 clientes API del frontend seguían usando `fetch` plano en vez de `authenticatedFetch` (ticket 078): `draftPersistenceApi.ts`, `geometryApplyApi.ts`, `mobExportApi.ts`, `referenceImagesApi.ts` (subida y listado), `textureUploadApi.ts` (solo subida), `thumbnailApi.ts` (solo subida). Sin ese cambio, el propio dueño de un proyecto privado se habría quedado sin poder guardar/exportar/pintar geometría/subir texturas o referencias de sus propios mobs -- la misma clase de regresión que motivó el hotfix 089, esta vez sobre casi todo el editor. Se adelantó ese cableado (parte del alcance ya previsto para el ticket `087`) por la misma razón que 089 lo hizo entonces: romper el uso normal de la app en dev no podía esperar.

### Ripple de un merge con `dev` (tickets 060-064/091-093, en curso en paralelo)
Como 091 (perfil de producto) se mergeó a `dev` mientras este ticket seguía abierto, hizo falta un merge con conflicto real en `ProjectService.java` (resuelto conservando ambos: `purgeAllForOwner` de 091 sin guard -- caller servidor-a-servidor, sin `callerId` contra qué comparar -- y el guard real en el resto de los métodos). El merge también expuso `InternalPurgeControllerTest` (091) a la nueva exigencia de `Authorization` en `GET /api/projects/{id}` -- corregido pasando el JWT real del dueño en esa prueba.

### Hallazgo real de infraestructura (afectó a ambos tickets, 085 y 091)
El primer deploy de `dev` tras mergear 091 falló: `GALGOTH_INTERNAL_SECRET` (nueva env var de 091) nunca se configuró de verdad en la VM, solo se agregó como `:?falta ...` obligatorio en `docker-compose.dev.yml`. Corregido generando un secreto real por ambiente y colocándolo en `deploy/.env.{dev,qa,prod}` de la VM -- el siguiente deploy (con 085 ya incluido) sí tuvo éxito.

### Sonar
Recurrencia del falso positivo S1135 con la palabra "todo" (2 ocurrencias más en este ticket, la 3ª y 4ª de la sesión) -- guardado como mejora de proceso en memoria (`sonar-s1135-todo-false-positive`), ver también rule 10 de CLAUDE.md.

### Tests y verificación en vivo
- Suite completa: backend 474/474, frontend 765/765, ambos en verde (incluye 8 tests nuevos explícitos para los ACs de este ticket en `ProjectControllerTest`: dueño real puede todo con su propio proyecto, lectura anónima de `PUBLIC` funciona, lectura anónima/ajena de `PRIVATE` responde 404, mutación ajena sobre público o privado responde 404).
- **Verificación en vivo contra DEV** (2 cuentas de prueba reales, creadas y eliminadas vía SQL al terminar): cuenta A crea un proyecto (nace `PRIVATE`); lectura anónima y de la cuenta B responden `404`; la propia cuenta A lee `200`; B intenta renombrar/borrar el proyecto de A -> `404` en ambos; B intenta renombrar un mob de A -> `404` (resuelto vía `mob.projectId`); subir un thumbnail sin `Authorization` -> `401` con la forma `ApiErrorResponse` correcta (confirma `ApiAuthenticationEntryPoint`); tras marcar el proyecto `PUBLIC` por SQL (no hay endpoint de visibilidad todavía, eso es 086), la lectura anónima pasa a `200`; `GET` del thumbnail (sin subir nada) sigue respondiendo sin exigir sesión, confirmando la excepción deliberada de los 3 endpoints de bytes crudos.
