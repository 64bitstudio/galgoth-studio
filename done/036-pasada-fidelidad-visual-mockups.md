# 036 — Pasada de fidelidad visual contra los mockups (Editor / Inicio / Detalle de proyecto)

## Objetivo
El PO no da VoBo visual a las 3 pantallas productivas existentes: se sienten
como scaffold funcional, no como la reproducción de los mockups aprobados
(`galgoth_studio_build_pack/mockups/`, especialmente `00_all_views.png`).
Pasada EXCLUSIVA de fidelidad visual -- sin funcionalidad nueva, sin cambios
de backend/contratos/UX -- que rehaga presentación, layout y componentes
para acercarlos a los mockups, conservando toda la lógica de negocio ya
implementada y probada.

Fuente de verdad visual: los mockups, no una reinterpretación. Los ASCII
del PO son una guía de composición, no el pixel-reference -- cuando hay
contradicción, gana el mockup real (ej. el panel de Asistente IA de
`06_edicion_ia.png`, no un "bottom bar" -- la interacción de panel-swap del
ticket 031 ya es una decisión funcional congelada, solo se restyla).

## Criterios de aceptación (TDD)
- Editor de modelo (mockup `05_editor_modelo.png`/`06_edicion_ia.png`):
  layout real `Jerarquía | Viewport 3D | Propiedades`, breadcrumb +
  tabs de workspace (Modelo/Textura/Animación, las 2 últimas deshabilitadas
  con motivo visible), toolbar de icon-buttons (Move/Scale/Rotate | Add
  cuboid/Add bone | Duplicate/Delete | Undo/Redo) con estados
  active/hover/disabled/focus-visible diseñados y tooltips, panel de
  Propiedades real (Posición/Tamaño/Rotación/Pivot del cuboid
  seleccionado, valores reales y editables via los métodos YA existentes
  de `draftModelStore`), jerarquía con iconos bone/cuboid + indentación +
  expand/collapse + hover/selected. Cero `<button>`/`<input>` sin estilo.
- Inicio/Mis proyectos (mockup `01_inicio_mis_proyectos.png`): cards CTA
  con icono + estados, sección "Proyectos recientes" con metadata real
  (mobCount/updatedAt ya existen en la API), ProjectCard con metadata
  visible (no solo nombre).
- Detalle de proyecto (mockup `12_detalle_proyecto.png`): header
  trabajado, buscador estilizado, grid de MobCard con metadata/estado
  visible, sin placeholders gigantes vacíos.
- Ningún endpoint, contrato, tabla ni comportamiento de negocio cambia --
  solo componentes Vue/CSS del frontend. Verificado con el mismo
  `./gradlew build` (backend intacto) y `npm run test`/`lint` en verde.
- Comparación visual real (screenshot vs. mockup) de las 3 pantallas antes
  de cerrar el ticket -- no alcanza con "compila y no rompe tests".

## Hecho

Las 3 pantallas verificadas visualmente en vivo (`npm run dev` + backend/Postgres/MinIO reales, no solo mocks) contra los mockups reales, con screenshots comparados lado a lado antes de cerrar el ticket.

**Editor de modelo** (mockup `05_editor_modelo.png`/`06_edicion_ia.png`):
- Layout real `Jerarquía | Viewport | Propiedades` (`MobEditor.vue` reestructurado) -- antes el panel derecho no existía.
- `EditorHeader.vue` (nuevo): breadcrumb "Galgoth Studio › {mob}" + `GTabs` Modelo/Textura/Animación (las 2 últimas deshabilitadas con "Próximamente" visible, ya soportado por `GTabs.vue` desde el ticket 002, solo faltaba usarlo acá).
- `EditorToolbar.vue` reescrito: grupos de `IconButton` (Move/Scale/Rotate | Add cuboid/Add bone | Duplicate/Delete | Undo/Redo) con separadores, estados active/hover/disabled/focus-visible reales (heredados del design system) y tooltip nativo -- cero `<button>` HTML sin clase. Se agregaron 10 íconos nuevos (`IconMove`/`IconScale`/`IconRotate`/`IconCuboid`/`IconBoneJoint`/`IconDuplicate`/`IconTrash`/`IconUndo`/`IconRedo`/`IconCamera`) y el componente `IconButton` (design system).
- `InspectorPanel.vue` + `InspectorField.vue` (nuevos): Posición/Tamaño/Rotación del cuboid seleccionado + Pivot del bone que lo contiene, con valores REALES y editables -- **cero funcionalidad nueva**: los 4 campos llaman a los métodos que YA existían en `draftModelStore` (`moveSelectedCuboid`/`resizeSelectedCuboid`/`rotateSelectedCuboid`/`setPivot`, los mismos que ya usaba el gizmo 3D), calculando el delta/escala necesario en el propio componente. Verificado en vivo editando "Rotación Y" de un cuboid real -- el brazo giró en el viewport de verdad.
- `HierarchyBoneNode.vue`/`HierarchyPanel.vue` reescritos: iconos reales bone/cuboid (`IconBoneJoint`/`IconCuboid`), indentación, expand/collapse real (chevron, estado local, default expandido -- mismo comportamiento visible de siempre), hover/selected con clases nuevas. El editor inline de pivote (inputs sueltos dentro del árbol) se movió al Inspector -- mismo método de store, sin duplicar lógica.
- Botón eliminar de bone restyleado (`GButton`) conservando la misma advertencia de cascada.

**Inicio/Mis proyectos** (mockup `01_inicio_mis_proyectos.png`):
- Card CTA "Crear un mob con IA" gana su ícono (`IconImage`, nuevo) -- antes solo texto.
- `ProjectCard.vue`: nueva línea de metadata real ("N mobs · Editado hoy/ayer/hace X días") vía `formatRelativeDate` (nuevo, puramente de presentación sobre `updatedAt`, un campo que YA existía en `ProjectSummary` -- no se agregó ningún dato nuevo a la API).

**Detalle de proyecto** (mockup `12_detalle_proyecto.png`):
- Buscador con ícono (`IconSearch`) integrado al input.
- Botón "Agregar mob" y CTA "+ Nuevo mob" migrados a `GButton`/ícono (`IconPlus`) en vez de `<button>` sin estilo.

**Explícitamente NO implementado (fuera de alcance, decisión consciente)**:
- El dropdown de orden "Última modificación" del mockup 12 -- no existe ningún mecanismo de ordenamiento hoy (ni backend ni frontend); agregarlo sería funcionalidad nueva, prohibida por este ticket.
- Los labels de `GStatusPill` ("Draft"/"Ready"/"In progress" en inglés vs. "Borrador"/"Listo"/"En proceso" del mockup) -- cambio de copy de un componente compartido y probado, fuera del alcance estrictamente visual/layout de este ticket; señalado para una decisión aparte del PO si lo quiere.
- El bug pre-existente de thumbnails (el gizmo de transformación queda "horneado" en la captura, visible en varias cards) -- no introducido por este ticket, es lógica de `ThreeViewportService.captureThumbnail()`, fuera de alcance (ticket dice explícitamente "no backend/no rediseño de lógica que ya funciona").

**Verificación real**: `./gradlew build` (backend, sin tocar, `git status` confirma cero archivos de `backend/` modificados), `npm run lint`/`vue-tsc -b`/`npm run test` (317 tests, incluye 2 archivos de test nuevos y 5 actualizados por el refactor de selectores -- mismo comportamiento, nueva forma de encontrar los elementos) y `npm run build` todos en verde. Las 3 pantallas abiertas en un navegador real contra un stack local completo (Postgres/MinIO/backend real), con datos reales (fixture completo de Carcomido, 24 cuboids/6 bones) -- no solo un mock aislado.
