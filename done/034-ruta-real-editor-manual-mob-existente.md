# 034 — Ruta real del editor manual sobre un mob existente

## Objetivo
Los tickets 031 (edición por IA), 032 (exportación) y 033 (Playwright E2E, que incluye explícitamente "editar a mano y Guardar") asumen una pantalla real de edición de un mob ya existente — pero esa ruta productiva nunca se construyó: el editor manual (tickets 016-018, `ThreeViewport.vue`/`HierarchyPanel.vue`/`EditorToolbar.vue`, todos reales y probados) solo se ejerce hoy vía `/dev/viewport-harness` (harness de desarrollo, fixture estático, nunca un `mobId` real de la URL). El ticket 030 dejó este gap documentado explícitamente (VoBo del PO) al resolver "Usar este modelo" navegando de vuelta a `/projects/:id` en vez de abrir un editor. El PO confirmó (vía `AskUserQuestion`) que este ticket debe resolverse antes de continuar con 031.

Monta `/projects/:projectId/mobs/:mobId/edit`, cargando el draft real del mob (`GET /api/mobs/{mobId}/draft`, ticket 020) en los componentes ya reales del editor manual — mismo patrón que `ViewportHarness.vue`, pero con un `mobId` real tomado de la URL en vez de un fixture estático. No es funcionalidad de editor nueva — es la ruta/wiring productivo real sobre piezas que ya existen y están probadas.

## Criterios de aceptación (TDD)
- Dado un mob con al menos una revisión/draft guardados (post "Usar este modelo", 030, o un "Guardar" manual previo), cuando se navega a `/projects/:projectId/mobs/:mobId/edit`, entonces el editor real (viewport + jerarquía + toolbar) carga y muestra el draft real de ese mob vía `GET /api/mobs/{mobId}/draft`.
- Dado un mob recién creado sin ningún draft todavía (`mobs.current_revision_number=0`, cero filas en `mob_drafts` — "Estado inicial de un mob" del diseño técnico), cuando se abre esa misma ruta, entonces el editor arranca desde un modelo vacío coherente con el `baseType`/`mobId`/`projectId` reales del mob — nunca un error ni una pantalla en blanco sin explicación.
- Dado el detalle de un proyecto (`ProjectDetail.vue`), cuando se hace click en la tarjeta de un mob (`MobCard.vue`, hoy sin ninguna navegación), entonces navega a esta ruta real.
- Dado el botón "Guardar" del editor sobre un mob cargado desde esta ruta, cuando se hace click, entonces persiste una revisión real del mob correcto (mismo mecanismo ya probado de 020/023) — verificado en vivo contra el backend real, no solo con un test.
- Dado que se navega directo a la URL de un `mobId` inexistente, cuando se abre, entonces se informa el error explícito (404) en vez de un editor roto/en blanco.

## Hecho

Ticket originado directamente por el PO (vía `AskUserQuestion`, no parte de la numeración original de 33 tickets) como precondición explícita antes de continuar con 031 — mismo mecanismo ya usado para resolver tensiones similares en 027/028/030 (un AC redactado antes de tiempo asume una pantalla que ningún ticket anterior construyó).

### Backend

- **`GET /api/mobs/{mobId}`** (`MobDetailController`, nuevo): ruta ya prevista desde el bootstrap del proyecto (`docs/API.md`, "Rutas previstas"), sin `projectId` en el path — mismo criterio que `MobDraftController`/`MobThumbnailController`. `MobService.get(mobId)` reutiliza el `toSummary` ya existente. `404` (`MOB_NOT_FOUND`) si el mob no existe.
- 2 tests backend nuevos (`MobDetailControllerTest`). **181 tests backend en total, 0 fallos.**

### Frontend

- **`MobEditor.vue`** (nuevo, `frontend/src/editor/`, ruta `/projects/:projectId/mobs/:mobId/edit`): carga en dos pasos — `GET /api/mobs/{mobId}` (resumen real) y `GET /api/mobs/{mobId}/draft` (020, primer cliente frontend real). `DRAFT_NOT_FOUND` se trata como caso ESPERADO (no un error): arranca desde `emptyMobProjectModel` con el `name`/`baseType` reales del mob. `MOB_NOT_FOUND` muestra un error explícito con link de vuelta al proyecto — nunca un editor roto o en blanco sin explicación.
- **`emptyMobProjectModel.ts`** (nuevo, `frontend/src/domain/`): extraído de `ai/generationEvents.ts` (`emptyPreviewModel`, ahora un thin wrapper) para compartirlo entre el pipeline de generación IA (029) y el editor manual real (034) sin duplicar la misma lógica ni crear una dependencia cruzada `editor→ai`/`ai→editor`.
- **`ProjectDetail.vue`**: cada `MobCard` del grid ahora es un `router-link` real hacia la ruta de edición — antes no había ninguna navegación desde la tarjeta.
- **`frontend/src/projects/mobsApi.ts`**: `getMob(mobId)` (nuevo). **`frontend/src/editor/draftPersistenceApi.ts`**: `getDraft(mobId)` (nuevo).
- 12 tests frontend nuevos (`MobEditor.spec.ts`, `emptyMobProjectModel.spec.ts`, ajustes en `mobsApi.spec.ts`/`draftPersistenceApi.spec.ts`/`ProjectDetail.spec.ts`). **283 tests frontend en total, 0 fallos.** `npm run lint`/`vue-tsc -b`/`npm run build` todos en verde.

## Verificación en vivo (Claude in Chrome, backend+Postgres+MinIO reales)

Todos los caminos verificados contra el backend real, sin mocks de frontend:

1. **Mob sin ningún draft todavía**: click en su tarjeta desde `ProjectDetail.vue` → editor real abre con jerarquía/viewport vacíos, sin error → click "Guardar" → "Guardado (revisión 1)." real. Confirmado en Postgres: `mobs.current_revision_number=1`, `mob_revisions(revision_number=1, created_by='user')` — distinto de `'ai'` (030), confirmando que el `createdBy` correcto se usa según el origen real del commit.
2. **Mob CON draft real** (el rig generado por IA de la verificación en vivo del ticket 030): navegación directa a su URL → carga la jerarquía COMPLETA (root/torso/cloth_tatter/crack_torso/head/left_eye/right_eye/left_arm/left_hand/crack_left_arm/right_arm/right_hand/crack_right_arm/left_leg/right_leg) y el rig humanoide real, geometría intacta, en el viewport 3D.
3. **`mobId` inexistente**: "Este mob no existe. Volver al proyecto" explícito, con link funcional — nunca una pantalla rota.

Sin errores de consola en ningún caso.

## Checklist de criterios de aceptación

- ✅ Mob con draft real → el editor carga y muestra su contenido real (verificado en vivo con el rig de IA completo).
- ✅ Mob sin ningún draft → arranca desde un modelo vacío coherente (`name`/`baseType`/`mobId`/`projectId` reales) — nunca un error.
- ✅ Click en `MobCard` desde `ProjectDetail.vue` → navega a la ruta real.
- ✅ "Guardar" sobre un mob cargado desde esta ruta → persiste una revisión real (verificado en vivo, `mob_revisions.created_by='user'`).
- ✅ `mobId` inexistente → error explícito (404 backend, mensaje claro en frontend), nunca un editor roto.
