# 068 — Homologación del shell Modelo/Textura + mejoras al editor de textura

## Objetivo

Feedback directo del PO sobre el editor de mob, con un mockup interactivo iterado en varias rondas (VoBo final: *"así tal cual está el diseño replícalo de manera EXACTA, en el proyecto, utiliza los mismos iconos, acomodos y tamaños"*, Artifact `https://claude.ai/code/artifact/be368ed5-93e4-404b-a33e-86d58eff7026`). El pedido original agrupaba varios hallazgos de homologación visual entre las tabs Modelo/Textura de `MobEditor.vue`, más un par de features nuevas descubiertas en el camino (undo/redo faltante en Textura, botón de mano, sidebar colapsable).

**Alcance de este ticket** (14 ajustes del mockup, ver su leyenda): todo lo que es homologación de layout/estilo + features mecánicas que reusan handlers ya existentes. **Deliberadamente fuera de alcance** (señalado explícitamente al PO, ver addendum de arquitectura): que el botón de IA abra un mismo drawer compartido en las dos tabs -- eso requiere extraer el contenido de `AiEditPanel.vue`/`TextureAiGeneratorScreen.vue` a componentes embebibles, una pieza más grande que amerita su propio ticket (069) para no mezclar un cambio de layout con un cambio de arquitectura de otro tamaño en el mismo PR.

## Criterios de aceptación (TDD)

- La fila superior (Reset cámara/Asistente IA/Exportar/Guardar) se muestra en las DOS tabs, no solo en Modelo.
- "Guardar" es un único botón compartido en esa fila (ya no vive dentro de `EditorToolbar.vue`/`TextureCanvas.vue`) que delega en el hijo activo sin duplicar lógica de guardado.
- "Exportar" tiene ícono en las dos tabs (antes no tenía ninguno).
- Modelo muestra el mismo indicador de guardado de 4 estados (punto + texto) que ya usa Textura, respaldado por un `dirty` real en `draftModelStore`.
- La toolbar-card de Textura nunca muestra scroll ni se parte en dos filas, sin importar el ancho de la ventana.
- Textura tiene un botón de mano para mover el lienzo con un click, sin depender solo de la barra espaciadora.
- El lienzo de Textura convive con el preview 3D vía un separador arrastrable (200–560px), sin romper el layout.
- Textura tiene deshacer/rehacer, reusando el mecanismo ya existente en `textureEditorStore`.
- El sidebar de navegación tiene un botón para colapsarlo a solo íconos, persistente entre navegaciones.

## Hecho

**Componentes ajustados:**
- `MobEditor.vue`: `.mob-editor__top-actions` deja de tener `v-if="activeTab === 'modelo'"` -- se muestra siempre. Nuevos `editorToolbarRef`/`textureCanvasRef` (template refs) + `activeSaveState`/`activeCanSave` (computed) + `handleTopSave()`/`handleIaButtonClick()` que delegan en el hijo activo. El botón de IA usa ahora variante `accent` (Modelo pasa a `primary` mientras el panel está abierto) -- mismo ícono/posición en las dos tabs, pero SIN cambiar su comportamiento actual (toggle vs navegación, ver "Fuera de alcance" arriba).
- `EditorToolbar.vue`: el botón "Guardar" se saca del template (sube a `MobEditor.vue`) -- expone `{ saveState, canSave, handleSave }` vía `defineExpose`. El mensaje efímero `saveMessage` se reemplaza por `TextureSaveStatus` (mismo componente que ya usaba Textura), respaldado por el nuevo `draftModelStore.dirty`.
- `TextureCanvas.vue`: se sacan los botones "Guardar" y "Generar con IA" (el primero sube a `MobEditor.vue` vía `defineExpose({ saveState, canSave, handleSave })`; el segundo se reemplaza por el botón de IA compartido de `MobEditor.vue`, mismo `router.push`). Nuevo botón de mano (`handToolActive`, mismo mecanismo de pan que la barra espaciadora). Nuevo separador arrastrable (`previewPanelWidthPx`, 200–560px) reemplaza el ancho fijo de 320px del panel de preview, llamando a `threeViewportService.resizeToContainer()` durante el arrastre. Nuevo grupo de Deshacer/Rehacer (reusa `textureEditorStore.undo()/redo()/canUndo/canRedo`, ya implementados desde el ticket 046 pero nunca cableados a ningún botón). Fix real de la toolbar: `flex-wrap: wrap` (causaba el partido en dos filas) pasa a `nowrap`, con el selector de región como única "válvula de presión" (su `GSelect` ya trunca con elipsis) y el resto de los controles con `flex-shrink: 0`.
- `GButton.vue`: suma `flex-shrink: 0` (hallazgo real: sin esto, competía por espacio en un toolbar apretado).
- `GSidebar.vue`: nuevo botón de colapsar (`IconSidebarToggle`, nuevo ícono) -- estado persistido en `localStorage` (`gsidebar-collapsed`), porque el sidebar se remonta en cada navegación de ruta.
- `draftModelStore.ts`: nuevo `dirty` (ref) + `markSaved()` -- `true` desde cualquier Command exitoso (incluyendo `commitExternalModel`) hasta el próximo `markSaved()`.
- Nuevos íconos: `IconHand.vue` (mano, mover lienzo), `IconExport.vue` (exportar/descargar, invertido respecto a `IconUpload.vue`), `IconSidebarToggle.vue` (panel + flecha, colapsar sidebar).

**Hallazgos reales encontrados en el camino:**
- El PO señaló, y se confirmó en el código, que el "scroll" reportado en la toolbar NO era un overflow-x -- era `flex-wrap: wrap` partiendo la fila en dos. Corregido con `nowrap` + una única válvula de presión, en vez de agregar cualquier mecanismo de scroll (que el PO explícitamente no quería).
- `textureEditorStore` ya tenía `undo()/redo()/canUndo/canRedo` completos desde el ticket 046 -- nunca se cableó ningún botón/atajo de teclado en la UI de Textura. No era solo un tema visual, era una función completa sin exponer.
- Reemplazar `saveMessage` (mensaje efímero) por un estado persistente requirió agregar tracking real de "cambios sin guardar" a `draftModelStore` (no existía) -- una adición de dominio genuina, no solo un cambio de UI.

**TDD real**: nuevos tests en `draftModelStore.spec.ts` (6, dirty/markSaved), `GSidebar.spec.ts` (4, colapsar), `EditorToolbar.spec.ts` (reescritos los de Guardar para invocar `handleSave()` expuesto en vez de clickear un botón que ya no existe), `TextureCanvas.spec.ts` (7 nuevos: mano + separador; 4 reescritos), `MobEditor.spec.ts` (4 nuevos: fila superior en las dos tabs, delegación de Guardar a cada hijo real). Todos confirmados en rojo genuino (`git stash` del archivo fuente correspondiente, re-corrida, restaurado) antes de contarlos como ciclo TDD válido. Frontend completo: 603/603 tests, `vue-tsc -b` y `eslint --max-warnings 0` sin hallazgos.

**Pendiente, señalado explícitamente al PO**: ticket 069 (Asistente IA como drawer compartido en las dos tabs) -- requiere extraer el contenido de `AiEditPanel.vue` (chico, 286 líneas, ya diseñado para emitir eventos) y `TextureAiGeneratorScreen.vue` (grande, ~965 líneas, pipeline SSE completo) a componentes embebibles dentro de un nuevo `GDrawer.vue` del design system. No se mezcló con este ticket para no combinar un cambio de layout con una extracción de componentes de otro tamaño en el mismo PR.
