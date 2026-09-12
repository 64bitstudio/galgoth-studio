# 072 — Rediseño de "Mis proyectos" (fidelidad visual estricta)

## Objetivo
Reconstruir la pantalla "Mis proyectos" (`ProjectsDashboard.vue`, en `/projects`) para que coincida visualmente con la referencia entregada por el Product Owner. Mismo flujo que el ticket 071: preview interactivo (Artifact) con VoBo del PO (*"doy vobo, esta perfecto"*) antes de implementar.

## Decisión del Product Owner (previa a implementar)
La referencia agrega un badge de estado por proyecto ("Activo"/"Draft") que no existía en el dominio (`projects` no tenía ningún campo de estado). Resuelto: **derivado en el backend a partir de los mobs del proyecto**, sin columna nueva -- "active" si el proyecto tiene al menos un mob fuera de `draft` (`in_progress`/`ready`), "draft" si todos sus mobs son draft o no tiene ninguno.

## Alcance

### Incluye
- Backend: `ProjectSummary.status` (derivado, `ProjectService.deriveStatus`, reutiliza la lista de mobs ya cargada para las miniaturas -- sin query adicional). `ProjectDetail`/otros endpoints sin cambios.
- Frontend: `ProjectsDashboard.vue` rediseñado -- título "Mis proyectos" + subtítulo (reemplaza el saludo "Buenos días" heredado de cuando compartía pantalla con Inicio, ticket 039/071), buscador de proyectos (client-side, mismo criterio que el buscador de mobs de `ProjectDetail.vue`), botón "+ Nuevo proyecto", y una card punteada "Nuevo proyecto" al final de la grilla (mismo patrón "+N" que ticket 071 en Home).
- El flujo "Crear un mob con IA" (con su selector de proyecto) se RETIRA de esta pantalla -- no está en la referencia, y el punto de entrada completo ya vive en Inicio desde el ticket 071. `AiMobProjectPickerDialog.vue` sigue existiendo (todavía lo usa `HomeView.vue`), solo deja de montarse acá.
- `ProjectCard.vue` rediseñado: badge de estado, miniaturas en tiles de tamaño fijo con "+N" como su propio tile (antes insignia superpuesta), menú ⋮ alineado al pie de la card.
- `relativeDate.ts`: agrega semanas/meses/años (antes se quedaba en "días" sin límite, ej. "hace 87 días"; la referencia pide "hace 1 semana"). Primer test dedicado para esta utilidad (no tenía ninguno).
- Token nuevo `--text-2xl` (28px) para el título de pantalla, mayor que el saludo de Inicio.
- Docs: `docs/API.md` actualizado (`ProjectSummary.status`).

### No incluye
- Cambiar cómo se calcula el status más adelante (ej. un campo editable manualmente) -- decisión explícita de mantenerlo derivado por ahora.
- Tocar `ProjectDetail.vue` (detalle de un proyecto) -- fuera de la referencia de este ticket.
- Extender la animación de diálogos/menús (ya resuelta en el 071) a pantallas fuera de Inicio/Mis proyectos.

## Criterios de aceptación (TDD)
- `GET /api/projects` incluye `status` ("active"/"draft") correctamente derivado (probado: sin mobs, todos draft, al menos uno fuera de draft).
- "Mis proyectos" muestra título+subtítulo reales (no el saludo de Inicio), buscador funcional (filtra por nombre, mensaje explícito sin coincidencias), botón "+ Nuevo proyecto" y card punteada "Nuevo proyecto" -- ambos abren el mismo modal de creación existente.
- Cada `ProjectCard` muestra su badge de estado, hasta 3 miniaturas + tile "+N" propio cuando `mobCount > 3`, y conserva las acciones existentes (Renombrar/Duplicar/Eliminar) sin regresión.
- Ya no existe ningún punto de entrada a "Crear un mob con IA" en esta pantalla.
- `formatRelativeDate` cubre días/semanas/meses/años con tests dedicados.
- Suite completa (backend + frontend) en verde, sin hallazgos nuevos de lint/type-check, build sin errores.
- Verificación visual en vivo contra la referencia en un viewport equivalente.

## Hecho

Implementado sobre el preview interactivo con VoBo del PO (`https://claude.ai/code/artifact/53f083de-237b-4321-bece-ad73138372b5`, *"doy vobo, esta perfecto"*, sin ajustes pedidos sobre el preview).

**Backend:**
- `ProjectSummary.status` (nuevo campo, `"active"`/`"draft"`) + `ProjectService.deriveStatus(mobs)` -- reutiliza la lista de mobs ya cargada para las miniaturas, sin query adicional. 3 tests nuevos en `ProjectControllerTest` (sin mobs, todos draft, al menos uno fuera de draft).
- `docs/API.md` actualizado.

**Frontend:**
- `ProjectsDashboard.vue` reescrito: título "Mis proyectos" + subtítulo (reemplaza el saludo "Buenos días" heredado de 039/071), buscador client-side, botón "+ Nuevo proyecto", card punteada "Nuevo proyecto" al final de la grilla (mismo criterio "+N" que Inicio, ticket 071). Se retira por completo el flujo "Crear un mob con IA" de esta pantalla (no está en la referencia; el punto de entrada ya vive en Inicio) -- `AiMobProjectPickerDialog.vue` sigue existiendo, solo deja de montarse acá.
- `ProjectCard.vue` reescrito: badge de estado (`status-pill`, nuevo, deliberadamente separado de `GStatusPill` -- estados de PROYECTO, no de mob), miniaturas en tiles de tamaño fijo (64px) con "+N" como su propio tile en vez de insignia superpuesta, menú ⋮ alineado al pie de la card.
- `relativeDate.ts`: agrega semanas/meses/años (antes se quedaba en "días" sin límite). Primer test dedicado (`relativeDate.spec.ts`, no existía ninguno) con reloj falso (`vi.useFakeTimers`/`vi.setSystemTime`) para fechas determinísticas.
- Token nuevo `--text-2xl` (28px, `tokens.css`) para el título de pantalla.
- Tests actualizados: `ProjectsDashboard.spec.ts` reescrito (sin el describe de "Crear un mob con IA"; nuevos tests de buscador y de la card "Nuevo proyecto"), `ProjectCard.spec.ts` reescrito (badge de estado, tile "+N" propio), fixtures de `ProjectSummary` en `HomeView.spec.ts`/`AiMobProjectPickerDialog.spec.ts`/`RecentProjectCard.spec.ts` actualizados con el campo `status` nuevo (requerido por el tipo).

**Hallazgo real durante los propios tests**: con el buscador nuevo, `ProjectsDashboard.vue` pasó a tener DOS `<input>` en el DOM a la vez (buscador + el del modal de crear/renombrar) -- varios tests que hacían `wrapper.find('input')` a secas (sin escopar) empezaron a encontrar el buscador en vez del input del modal. Detectado por los tests fallando genuinamente (no en silencio), corregido escopando a `.app-dialog` en cada caso.

**Verificación en vivo** (mismo stack real de tickets anteriores): comparado contra la referencia en `/projects` -- layout, buscador (con mensaje "Ningún proyecto coincide con..." cuando no hay resultados, card "Nuevo proyecto" siempre disponible), badge de estado, y menú ⋮ funcionando correctamente con datos reales.

**Feedback del PO en vivo (transición de diálogos fuera de Inicio)**: el modal "Agregar mob" (CTA "Nuevo mob"/"Agregar mob" del detalle de proyecto) no tenía transición de entrada/salida -- gap ya señalado explícitamente en el ticket 071 ("el resto de pantallas... queda sin animar por ahora") y ahora extendido: `<Transition name="app-dialog">` (mecanismo del 071, `AppDialog.vue`) aplicado también en `ProjectDetail.vue` (`AddMobModal`/`ProjectNameModal`/`MobRenameDialog`/`ConfirmDialog`), `ProjectsDashboard.vue` (`ProjectNameModal`/`ConfirmDialog`, quedaron sin envolver en la reescritura de este mismo ticket) y `MobEditor.vue` (`ConfirmDialog` de confirmación de resize). `AddMobModal.vue` no usa `AppDialog` internamente (predata su extracción, tiene su propio `<dialog>` crudo) -- funciona igual porque las clases de transición son genéricas (`.app-dialog-enter-active` etc.), no dependen de que el elemento envuelto sea literalmente `.app-dialog`. Verificado en vivo con la transición ralentizada artificialmente (`javascript_tool`): clases `app-dialog-enter-from`/`app-dialog-enter-active` presentes y `opacity` animando de 0 a 1 en el modal real de "Agregar mob".

**Estado final**: backend 100% de tests en verde (Testcontainers); frontend 668/668 tests en verde, `vue-tsc -b`/`eslint --max-warnings 0` sin hallazgos, `npm run build` sin errores.

**Gate de autorización del requerimiento** (mismo criterio que ticket 071): implementación LOCAL únicamente -- sin commits, sin push, sin ramas, sin PR. Ticket queda en `in-process/` hasta que el PO revise el resultado en vivo y autorice el flujo git normal.
