# 088 — Sección Explorar (frontend)

## Objetivo
Ver `docs/definiciones/proyectos-por-usuario-y-explorar.md` (VoBo de
Marco recibido) — HU-5 y HU-6. El ítem "Explorar" ya existe como stub en
`GSidebar.vue` (`{ key: 'explore', label: 'Explorar', icon: IconExplore }`)
sin ruta detrás. Este ticket construye la pantalla real.

**Depende de:** `086` (endpoint `GET /api/explore/projects` y detalle
público ya deben existir).

## Alcance
- **Sí incluye:**
  - Nueva ruta `/explore` → `ExploreView.vue`: galería de tarjetas
    (miniatura, nombre, descripción, `ownerDisplayName`) consumiendo
    `GET /api/explore/projects`, **sin** exigir sesión (guard del ticket
    `087` no aplica aquí).
  - Nueva ruta `/explore/:id` → `ExploreProjectDetail.vue`: ficha del
    proyecto público + lista de sus mobs (nombre, miniatura, estado) en
    modo estrictamente lectura — sin ningún botón de editar, borrar,
    exportar o duplicar (fuera de alcance, ver documento de definición).
  - `GSidebar.vue`: `handleSidebarSelect` en las vistas que la usan
    (`HomeView.vue`, `ProjectsDashboard.vue`, etc.) navega de verdad a
    `/explore` cuando `key === 'explore'` — deja de ser un no-op.
  - Estado vacío ("Todavía no hay proyectos públicos") si Explorar no
    tiene resultados — nunca una pantalla en blanco sin explicación
    (mismo criterio de "nunca fingir funcionalidad" del resto del
    proyecto).
- **No incluye:** visor 3D en modo lectura (el detalle de mobs se queda
  en nombre/miniatura/estado, sin abrir `MobEditor.vue`), duplicar desde
  Explorar, búsqueda/filtros.

## Criterios de aceptación (TDD)
- Visitante sin sesión navega a `/explore` y ve los proyectos públicos
  reales.
- Abrir un proyecto público desde Explorar muestra su ficha y mobs, sin
  ninguna acción de edición visible.
- Un proyecto privado nunca es alcanzable vía `/explore/:id` (backend ya
  responde `404`, el frontend muestra el estado "no encontrado", no un
  error genérico).
- Suite de tests del frontend en verde (Vitest).
- Verificación en vivo contra DEV: publicar un proyecto real desde una
  cuenta, confirmar que aparece en `/explore` navegando SIN sesión
  iniciada (ventana privada o sesión cerrada), y que su detalle se ve
  correctamente en modo lectura.

## Hecho

**PR:** [#129](https://github.com/64bitstudio/galgoth-studio/pull/129), mergeado a `dev` (`3471404`). Deploy real a DEV verificado en verde (build 128 de Jenkins).

**Implementado tal cual el alcance:**
- `/explore` → `ExploreView.vue`: galería de `ExploreProjectCard.vue` (miniaturas, nombre, descripción, `ownerDisplayName`) consumiendo `GET /api/explore/projects` (ticket 086), sin sesión. Estado vacío explícito ("Todavía no hay proyectos públicos.") y mensaje de error explícito ante una falla de carga real -- nunca una pantalla en blanco.
- `/explore/:id` → `ExploreProjectDetail.vue`: ficha + mobs (`ExploreMobCard.vue`) en modo estrictamente lectura -- sin renombrar, sin menú ⋮, sin "Agregar mob"/"Crear con IA", sin toggle de visibilidad. Reutiliza `getProject`/`listMobs` tal cual (mismos endpoints que "Mis proyectos", públicamente legibles para un proyecto `PUBLIC` desde el ticket 085) -- ningún cliente HTTP nuevo salvo `listExploreProjects()`.
- `GSidebar.vue`: "Explorar" deja de ser un no-op en las 6 pantallas con su propio `handleSidebarSelect` (`HomeView`, `ProjectsDashboard`, `ProjectDetail`, `AiMobWizard`, `ExportScreen`, `MobEditor`) -- mismo patrón ya usado ahí, sin introducir una abstracción compartida nueva (consistente con el resto del código base, que no comparte esta función entre pantallas).
- Un proyecto privado/inexistente en `/explore/:id` siempre muestra "Este proyecto no existe o ya no está disponible." -- nunca el texto crudo del backend ni un error genérico (a diferencia de `ProjectDetail.vue` del ticket 087, acá no hay ambigüedad de dueño que resolver con un login: esta pantalla nunca asume que el visitante es el dueño).

**Tests:** 4 archivos nuevos en `explore/__tests__/` (18 tests) + 1 test nuevo en `projectsApi.spec.ts` (`listExploreProjects`) + 1 test nuevo por cada una de las 6 pantallas con sidebar propio confirmando que "Explorar" navega de verdad + 2 tests nuevos en `router.spec.ts` (`/explore` y `/explore/:id` no exigen sesión). 801 tests frontend en verde, `npm run build` (`vue-tsc -b` real) y `eslint --max-warnings 0` limpios.

**Hallazgo de proceso (preventivo, sin incidente real esta vez):** se encontró y reformuló proactivamente una ocurrencia más de la palabra "todo" en un comentario de `HomeView.vue` (archivo tocado por este ticket pero no en esa línea) antes de abrir el PR, aplicando la lección ya memorizada de tickets anteriores (`sonar-s1135-todo-false-positive`) en vez de esperar a que el Quality Gate la marcara.

**Verificación en vivo contra DEV real (navegador real, Chrome, no simulada):**
- Cuenta de prueba real registrada y logueada vía el formulario real (no la API directa) contra `auth-dev.64bitstudio.com`.
- Proyecto real creado y publicado (`Hacer público` desde el menú ⋮, ticket 086/087).
- **Pestaña nueva del navegador** (sin sesión -- `sessionStorage` es por pestaña, mismo mecanismo que ya usa la app) navega a `/explore`: el proyecto recién publicado aparece con "por QA Ticket088", sin exigir ningún login.
- Abrir la tarjeta navega a `/explore/{id}` y muestra la ficha de solo lectura (breadcrumb "Galgoth Studio › Explorar › QA Ticket 088", sin ningún botón de edición, estado vacío "Este proyecto todavía no tiene mobs.").
- Navegar a `/explore/{uuid-inexistente}` sin sesión muestra "Este proyecto no existe o ya no está disponible." -- confirma que el frontend nunca expone el 404 crudo del backend.
- Limpieza posterior: proyecto y cuenta de prueba borrados a mano vía SQL directo en las bases de DEV (mismas tablas y FKs que en los tickets 086/087).
