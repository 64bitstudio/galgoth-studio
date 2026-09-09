# 019 — Command stack Undo/Redo

**Milestone:** M2 · **Depende de:** 018 · **HUs:** HU-08 · **Épica:** 015

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §4, HU-08). Implementar la pila de `Command` en Pinia sobre el draft en memoria — cada edición manual genera un Command; ninguno crea una `mob_revision` por sí solo.

## Criterios de aceptación (TDD)
- Dado una serie de cambios manuales, cuando se presiona Undo repetidamente, entonces el draft regresa paso a paso en el orden inverso exacto — ninguna `mob_revision` se crea ni se destruye en el proceso.
- Dado que se deshicieron cambios, cuando se presiona Redo, entonces el draft avanza hasta el estado más reciente.
- Dado un cambio nuevo aplicado después de deshacer, cuando ocurre, entonces se descarta la rama de redo pendiente (historial lineal estándar).

## Hecho

Implementado en `frontend/src/editor/draftModelStore.ts` (extendido, no un store nuevo) + `EditorToolbar.vue` (detalle completo en `docs/COMPONENTES.md`/`docs/ARQUITECTURA.md`):

- **Diseño del Command**: no es una clase propia -- es la referencia al `MobProjectModel` inmediatamente anterior a cada edición exitosa, guardada en `undoStack: MobProjectModel[]`. Esto es seguro y barato sin clonar nada porque toda función de `geometryOperations.ts` (018) es pura -- nunca muta su modelo de entrada, siempre retorna uno nuevo vía spread -- así que las referencias históricas nunca se corrompen por una mutación posterior.
- **`recordCommand(previous)`**: se llama SOLO tras una mutación exitosa (dentro de `apply()` y de `addCuboid`/`addBone`/`duplicate`, que tienen su propio try/catch), empujando el modelo previo a `undoStack` y VACIANDO `redoStack` (AC #3: descarta la rama de redo pendiente). Una operación rechazada (geometría inválida, referencia inexistente) nunca llama a `recordCommand` -- no genera nada que deshacer.
- **`undo()`/`redo()`**: mueven el modelo actual entre las dos pilas, reasignando `model` -- no hacen nada más. Ninguno de los dos toca el concepto de `mob_revision` (que ni siquiera existe en este store todavía, llega en el 020) -- la ausencia total de esa noción aquí es en sí misma la garantía de que Undo/Redo jamás puede crearla ni destruirla (AC #1).
- **`load()` reinicia ambas pilas**: cargar un mob nuevo empieza una historia de edición nueva, no hereda la del mob anterior.
- **`canUndo`/`canRedo`** (computed): exponen si hay algo en cada pila, para deshabilitar los botones del toolbar.
- **`EditorToolbar.vue`**: botones Undo/Redo (deshabilitados según `canUndo`/`canRedo`) + atajos de teclado Cmd/Ctrl+Z (deshacer) y Cmd/Ctrl+Shift+Z (rehacer) -- ignorados mientras el foco esté en un `<input>`/`<textarea>` (ej. editando un pivote) para no pelear con el undo nativo del propio campo de texto.
- **Tests**: 15 tests nuevos (11 en `draftModelStore.spec.ts` cubriendo cada AC explícitamente -- incluyendo una serie de 3 movimientos deshechos en el orden inverso exacto, redo hasta el estado más reciente, descarte de la rama de redo tras un Command nuevo, que una operación rechazada no genera Command, y que `load()` reinicia las pilas; 4 en `EditorToolbar.spec.ts` para botones + atajos de teclado + el guard de foco-en-input). 134 tests totales en el frontend, todos en verde. `vue-tsc -b`, `npm run lint` y `npm run build` en verde. Cobertura del proyecto: 95.56% (statements).
- **Verificación visual en vivo** (Claude in Chrome, `/dev/viewport-harness` con el fixture real Carcomido): Delete → Undo (restaura el cuboid, 23→24) → Redo (lo vuelve a eliminar, 24→23) confirmado tanto con los botones del toolbar como con los atajos de teclado reales (Ctrl+Z / Ctrl+Shift+Z), sin errores de consola.

### Hallazgos y decisiones de diseño no explícitas en el AC

- **Dónde vive la pila**: el AC pide "la pila de Command en Pinia" sin especificar si es un store dedicado o parte de uno existente. Se extendió `draftModelStore.ts` en vez de crear un `commandStackStore.ts` separado -- el Command stack solo tiene sentido acoplado 1:1 al `model` que gobierna, y un store separado habría necesitado sincronizar dos fuentes de verdad sin ganar nada a cambio.
- **Atajos de teclado**: no pedidos explícitamente por el AC (que solo describe "presionar Undo/Redo"), pero agregados como una extensión de bajo riesgo del mismo alcance -- Cmd/Ctrl+Z y Cmd/Ctrl+Shift+Z son la convención universal para esta funcionalidad y el master prompt ya lista "Undo/Redo" como herramienta de toolbar. Se documentan explícitamente aquí para que quede trazable como una decisión, no un descuido de alcance.
- **Selección tras Undo/Redo**: deliberadamente NO se toca `selectionStore.selectedCuboidId` al deshacer/rehacer. Si la selección queda apuntando a un cuboid que ya no existe tras un Undo, el mecanismo existente de `ThreeViewport.vue` (`findCuboidMesh` no lo encuentra → `transformControls.detach()`) ya maneja ese caso con gracia, sin necesitar lógica nueva.
- **CI/Sonar**: ambos gates verificados manualmente contra `sonarqube-db` antes de mergear (gap conocido desde el ticket 008) -- sin hallazgos nuevos esta vez.
