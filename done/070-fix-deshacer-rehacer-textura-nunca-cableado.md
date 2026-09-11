# 070 — Fix: Deshacer/Rehacer de Textura nunca se cableó a ningún botón (a pesar de estar documentado como hecho en el 068)

## Objetivo
El PO reportó que faltaban los botones de deshacer/rehacer en la toolbar de Textura (`Captura de pantalla 2026-09-11 a la(s) 9.46.04 a.m.`). Al revisar el código: `done/068-homologacion-shell-modelo-textura.md` documentaba explícitamente *"Nuevo grupo de Deshacer/Rehacer (reusa `textureEditorStore.undo()/redo()/canUndo/canRedo`, ya implementados desde el ticket 046 pero nunca cableados a ningún botón)"* como parte de lo entregado -- pero `git log`/`grep` sobre TODOS los commits de 068 confirmó que ese botón **nunca existió en el código**, en ningún commit. El mecanismo del store sí estaba completo y correcto (`textureEditorStore.ts`, desde 046); lo que faltaba, y lo que sigue faltando hasta este ticket, era la UI.

**No se trata de una regresión de 069** -- se verificó explícitamente que el botón tampoco existía en el primer commit de 068 (`a81f350`), antes de cualquier trabajo de 069.

## Criterios de aceptación (TDD)
- La toolbar de Textura tiene botones "Deshacer"/"Rehacer", deshabilitados cuando no hay nada que deshacer/rehacer.
- Clic en "Deshacer" revierte el último patch (mismo mecanismo que Modelo); clic en "Rehacer" lo vuelve a aplicar.
- `Ctrl`/`Cmd`+`Z` deshace, `Ctrl`/`Cmd`+`Shift`+`Z` rehace -- mismo atajo real que `EditorToolbar.vue` (Modelo), nunca disparado con el foco en un campo de texto.

## Hecho

**Causa raíz**: gap de documentación vs. código real -- el ticket 068 quedó cerrado con una afirmación de "Hecho" que no reflejaba el estado real del repositorio. El mecanismo de dominio (`textureEditorStore.undo()/redo()/canUndo/canRedo`) sí existía y funcionaba correctamente desde el 046; solo faltaba la UI (botones + atajo de teclado) en `TextureCanvas.vue`.

**Cambios**:
- `TextureCanvas.vue`: nuevo `tb-group` con `IconButton` "Deshacer"/"Rehacer" (`IconUndo`/`IconRedo`, ya existían en el design system -- los usa `EditorToolbar.vue`), `:disabled` sobre `!textureEditorStore.canUndo`/`!textureEditorStore.canRedo`, `@click` llama a `textureEditorStore.undo()`/`redo()`. `handleWindowKeydown` gana el mismo atajo `Ctrl`/`Cmd`+`Z`/`Shift`+`Z` que `EditorToolbar.vue`, respetando `isEditableTarget`.
- **`keyboardShortcuts.ts`** (nuevo, `frontend/src/editor/`): extrae `MODIFIER_KEY`/`isEditableTarget`, antes duplicados verbatim en `EditorToolbar.vue` -- se hubieran triplicado con este fix, se extraen a un solo lugar y `EditorToolbar.vue` se refactoriza para usarlo (sin cambio de comportamiento).

**TDD real**: `keyboardShortcuts.spec.ts` (4 tests, nuevo). `TextureCanvas.spec.ts`: 5 tests nuevos (deshabilitados por default, Deshacer revierte, Rehacer reaplica, atajo de teclado deshace/rehace, atajo ignorado con foco en un `<input>`). Todos confirmados en rojo genuino antes de implementar (los 2 primeros por el botón inexistente, los de teclado por el bug real de `pixelAt` mostrando el patch sin revertir). Frontend completo: 622/622 tests, `vue-tsc -b`/`eslint --max-warnings 0` sin hallazgos, `npm run build` sin errores.

**Mejora al propio proceso (regla 10)**: este gap existió porque nadie verificó, al cerrar 068, que cada línea de la sección "## Hecho" correspondiera a código real -- se confió en lo que se pensaba haber hecho. El skill `cerrar-ticket` ya pide revisar criterios de aceptación antes de mover a `done/`, pero no exige un chequeo cruzado explícito código-vs-`## Hecho` ítem por ítem. Vale la pena reforzar esa instrucción del skill para que no vuelva a pasar (una lista de features documentadas no reemplaza abrir el archivo y confirmar que están).
