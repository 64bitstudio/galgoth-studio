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
(se completa al cerrar el ticket)
