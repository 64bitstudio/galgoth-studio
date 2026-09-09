# 022 — CRUD de mobs + detalle de proyecto + modal "Agregar mob"

**Milestone:** M3 · **Depende de:** 021 · **HUs:** HU-03, HU-04

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Épica A). Detalle de proyecto con grid de mobs, búsqueda, y el flujo de "Agregar mob" (preservando la idea útil de `add_mob_modal_legacy.png`).

## Criterios de aceptación (TDD)
- Dado el detalle de un proyecto, cuando se hace clic en "Agregar mob", entonces se abre el flujo de creación de mob.
- Dado que se completa la creación, cuando se confirma, entonces el mob aparece con estado "Draft", `current_revision_number=0` y sin fila en `mob_drafts`.
- Dado el detalle de un proyecto con varios mobs, cuando se usa el buscador, entonces la lista se filtra por nombre.
- Dado el grid de mobs, cuando se renderiza, entonces muestra estado (Ready/In progress/Draft) por cada mob.

## Hecho

**Interpretación de "preservar la idea útil de `add_mob_modal_legacy.png`"**, documentada explícitamente ya que ese mockup es de OTRO producto ("Texture Studio MC") y no traspasa 1:1 al dominio de Galgoth: ese legacy modal deja elegir un mob ESPECÍFICO de un catálogo de vanilla Minecraft (Creeper, Zombi, ...) para re-texturizarlo -- un concepto ajeno a Galgoth (los mobs se generan/editan libremente, no son reskins de mobs vanilla). La idea útil que sí aplica -- una selección categorizada y enfocada antes de confirmar -- se adaptó al campo real del dominio: `baseType` (los 5 valores del `CHECK` de `mobs.base_type`, ticket 003), no un catálogo de bestiario.

Backend (`backend/src/main/java/com/galgothstudio/backend/project/mob/`, paquete nuevo):
- **`MobService`**: `create`/`list`, alcance deliberadamente acotado al AC de este ticket -- Rename/Delete/Duplicate a nivel de MOB individual no están pedidos aquí (a diferencia de proyectos en el 021) y no se inventaron. `create` valida nombre no vacío y `baseType` contra la whitelist real de 5 valores del `CHECK` constraint (`humanoid`/`arachnid`/`quadruped`/`flying`/`custom`) -- rechazando con `400 INVALID_MOB_REQUEST` antes de que un valor inválido llegue a la base de datos como una `DataIntegrityViolationException` genérica. Un mob nuevo SIEMPRE arranca `status="draft"` (nunca se acepta del cliente) + `current_revision_number=0` (default de columna) + sin fila en `mob_drafts` (no se crea aquí, solo en el primer autosave/Guardar, ticket 020).
- **`MobController`** (`POST`/`GET /api/projects/{projectId}/mobs`) + extensión de `ApiExceptionHandler` (`INVALID_MOB_REQUEST`).
- **Tests**: 6 nuevos (`MobControllerTest`, Testcontainers + MockMvc + Postgres real, uno por AC + casos de proyecto/nombre/baseType inválidos) -- 105 tests totales en backend, todos en verde.

Frontend (`frontend/src/projects/`, reemplaza `ProjectDetailPlaceholder.vue` del ticket 021):
- **`ProjectDetail.vue`** (ruta `/projects/:id`, mockup 12): header + botón "+ Agregar mob" + buscador (filtro **client-side** por nombre, AC #3 -- sin parámetro de búsqueda en el backend, mismo criterio de simplicidad que el dashboard de proyectos) + grid de `MobCard` + tarjeta CTA "+ Nuevo mob".
- **`MobCard.vue`**: miniatura placeholder (mismo criterio que `ProjectCard.vue`, sin pipeline de thumbnails todavía) + nombre + `GStatusPill` (AC #4) -- traduce `in_progress` (BD, guion bajo) a `in-progress` (prop del componente, guion medio).
- **`AddMobModal.vue`**: mismo patrón de `<dialog>` nativo que `ProjectNameModal.vue` (ticket 021) -- nombre + 5 pills de `baseType` (Humanoide/Arácnido/Cuadrúpedo/Volador/Personalizado), "Humanoide" preseleccionado.
- **`mobsApi.ts`**: mismo patrón que `projectsApi.ts`, reutiliza la clase `ApiError` para errores consistentes entre ambos.
- **`ProjectDetailPlaceholder.vue` y su test file eliminados** (reemplazados por `ProjectDetail.vue`/`ProjectDetail.spec.ts`).
- **Tests**: 23 nuevos (`mobsApi` 3, `AddMobModal` 6, `MobCard` 6, `ProjectDetail` 8) menos 4 eliminados (`ProjectDetailPlaceholder.spec.ts`) -- 198 tests totales en frontend, todos en verde. `vue-tsc -b`, `npm run lint`, `npm run build` en verde. Cobertura del proyecto: 95.27%.

### Verificación en vivo (Claude in Chrome, backend real vía `./gradlew bootRun` + Postgres real vía `docker compose`, no solo tests)

Grid con los 5 mobs reales de un proyecto ya existente (sembrados directo en Postgres en la verificación del ticket 021), cada uno con su pill "Draft" (AC #4); buscador filtrando por nombre en tiempo real contra el grid (AC #3, ej. "mob3" deja solo esa tarjeta); "Agregar mob" (AC #1) abre el modal con foco automático en el nombre (mismo `<dialog>` nativo del 021); creado un mob real ("Serpentario", tipo Arácnido) end-to-end (AC #2) -- confirmado no solo en el grid sino DIRECTAMENTE contra la base de datos real vía `psql`: `base_type='arachnid'`, `status='draft'`, `current_revision_number=0`, `count(*)=0` en `mob_drafts` para ese mob. Sin errores de consola en toda la sesión.

### Alcance no cubierto, documentado explícitamente (no un descuido)

- Rename/Delete/Duplicate de un mob individual no están en el AC de este ticket -- no se agregaron endpoints ni UI para ellos.
- El thumbnail de cada mob sigue siendo siempre un placeholder genérico (sin pipeline de generación de thumbnails todavía, épica futura) -- consistente con el mismo estado del dashboard de proyectos (021).
- La fecha relativa "Editado hoy/ayer/hace N días" del mockup 12 no se implementó en el header -- no está en el AC de este ticket (solo pide nombre + conteo), se deja para cuando exista un requisito real que la necesite.
