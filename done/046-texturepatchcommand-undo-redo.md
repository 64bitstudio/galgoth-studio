# 046 — TexturePatchCommand — Undo/Redo de textura por patches

**Milestone:** M8 · **Depende de:** 019, 040 · **HUs:** HU-27, HU-32 · **Épica:** K (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §9). El Undo/Redo de textura NO puede snapshotear el bitmap completo por cada trazo (inviable en memoria). Se implementa `TexturePatchCommand { rect, beforePixels, afterPixels }` acotado al rectángulo mínimo tocado, sobre una pila (`textureEditorStore.ts`) 100% independiente de la de geometría (018/019) — Ctrl+Z en el tab Textura nunca cruza al tab Modelo.

## Criterios de aceptación (TDD)
- Dado un trazo de pincel completo (`pointerdown` → varios `pointermove` → `pointerup`), cuando se registra en el historial, entonces genera EXACTAMENTE UN `TexturePatchCommand` (bounding box acumulado del trazo completo) — nunca uno por cada evento `pointermove` intermedio.
- Dado un uso de la Cubeta sobre una región contigua, cuando se aplica, entonces genera un `TexturePatchCommand` cuyo `rect` es el bounding box de la región rellenada.
- Dado un `TexturePatchCommand` con un `rect` pequeño sobre un atlas grande, cuando se mide el tamaño de `beforePixels`/`afterPixels`, entonces es proporcional al área del `rect`, nunca al área total del atlas (test explícito de tamaño, para blindar contra una regresión a snapshot completo).
- Dado un Undo sobre el último `TexturePatchCommand`, cuando se ejecuta, entonces aplica `beforePixels` sobre `rect` — operación O(área del rect). Dado un Redo posterior, entonces aplica `afterPixels`.
- Dado la pila de Undo/Redo de textura (`textureEditorStore.ts`) y la de geometría (`draftModelStore`, 019), cuando se hace Ctrl+Z estando en el tab Textura, entonces solo afecta la pila de textura — un cambio de geometría pendiente en la otra pila permanece intacto (test cruzado explícito).

## Hecho

Implementado el mecanismo de Command/Undo-Redo de textura (Diseño técnico §9),
sin canvas ni herramientas de pintado — eso es el ticket 047, todavía no existe.

- **`frontend/src/editor/texture/TexturePatchCommand.ts`**: interfaz
  `TexturePatchCommand { rect: TextureRect, beforePixels: Uint8ClampedArray,
  afterPixels: Uint8ClampedArray }` + `TextureRect { x, y, width, height }`,
  con el mismo path que usa el propio Diseño técnico §9 como ejemplo.
- **`frontend/src/editor/texture/textureEditorStore.ts`** (Pinia, Composition
  API — mismo estilo que `draftModelStore.ts`): pila de Undo y pila de Redo de
  `TexturePatchCommand`, **100% independiente** de `draftModelStore` (no la
  importa, no comparte ningún estado ni módulo con ella).
  - `loadAtlas(width, height, pixels?)`: inicializa/reemplaza el atlas
    vigente y limpia ambas pilas (mismo criterio que `draftModelStore.load()`
    — cargar un atlas es el inicio de una historia de edición, no un paso
    dentro de una existente).
  - `readRegion(rect)`: copia de solo lectura de los píxeles vigentes de
    `rect` — pensado para que el caller (ticket 047) capture `beforePixels`
    antes de pintar.
  - `recordPatch(rect, beforePixels, afterPixels)`: aplica `afterPixels`
    sobre el atlas en memoria (para que quede consistente con lo ya pintado
    por el caller) y empuja el Command a la pila de Undo, limpiando la de
    Redo — mismo comportamiento estándar de cualquier Command stack, igual
    que `draftModelStore.recordCommand()`.
  - `undo()`/`redo()`: aplican `beforePixels`/`afterPixels` sobre `rect`
    (`writeRect`, O(área del rect) — nunca reescriben el atlas completo) y
    mueven el mismo Command entre las dos pilas.
  - `undo()`/`redo()`/`recordPatch()` son no-ops explícitos y no lanzan si
    no hay atlas cargado o la pila correspondiente está vacía (mismo
    criterio defensivo que `draftModelStore`).

**Decisión de diseño — de dónde sale el bitmap sobre el que se aplican los
patches** (el canvas real del editor de textura no existe todavía en este
ticket): el store mantiene el **atlas completo en memoria como estado**
(`atlas: { width, height, pixels: Uint8ClampedArray }`, mismo layout RGBA que
`ImageData.data`) — necesario porque aplicar/deshacer un patch requiere algo
concreto sobre lo que escribir los píxeles, y el ticket 047 todavía no monta
ningún canvas real que se lo provea. El ticket 047 le pasará el atlas real vía
`loadAtlas()` al montar el editor, usará `readRegion()` para capturar
`beforePixels` antes de pintar, y llamará a `recordPatch()` después de cada
trazo/fill/paste ya aplicado a su propia copia visible. Esto NO viola el AC de
tamaño acotado: lo que debe ser proporcional al `rect` es el tamaño de CADA
Command histórico, no el tamaño del estado actual (una sola copia del atlas
completo es inevitable en cualquier diseño — es lo mismo que `MobProjectModel`
completo en `draftModelStore`, que tampoco es "proporcional a la última
edición"). `atlas` y ambas pilas usan `shallowRef` (no `ref`): son buffers
binarios grandes y la reactividad profunda de Vue por índice sobre un
`Uint8ClampedArray` no aporta nada aquí — se llama `triggerRef` explícitamente
tras mutar el buffer en el lugar para que cualquier consumidor reactivo (p.
ej. un componente que muestre el atlas) se entere del cambio.

**Divergencia deliberada del patrón `draftModelStore`, explicada**: en
`draftModelStore` cada Command guarda el `MobProjectModel` COMPLETO anterior
(barato porque las operaciones de geometría son puras e inmutables); aquí
cada Command NUNCA guarda una copia del atlas completo, solo los píxeles del
`rect` — es exactamente el problema que este ticket existe para resolver
(snapshotear el bitmap completo por trazo es inviable en memoria). Por eso
`undo()`/`redo()` no reasignan un estado completo como en `draftModelStore`,
sino que escriben in-place solo el área de `rect` sobre el atlas vigente.

**Aclaraciones sobre el alcance real de los tests (sin parches silenciosos)**:
los AC del ticket están redactados en términos de herramientas que no existen
todavía (`pointerdown`/`pointermove`/`pointerup` de un trazo de pincel, uso de
la Cubeta) — no hay canvas ni herramientas de pintado en este ticket (son el
047). Siguiendo la instrucción explícita de la tarea, un trazo completo se
simula como UNA sola llamada a `recordPatch()` (el mecanismo del store no
distingue de dónde viene el `rect`/`beforePixels`/`afterPixels` — sea de un
trazo de pincel, de la Cubeta, de un paste o de un Apply de IA, todos pasan
por la misma función y producen exactamente un Command). No se agregó una
herramienta "Cubeta" simulada porque hacerlo sería inventar comportamiento de
pintado fuera de alcance de este ticket; el test de tamaño acotado (AC #3)
cubre el caso general "rect pequeño sobre atlas grande" sin acoplarlo a una
herramienta concreta.

**Tests** (`frontend/src/editor/texture/__tests__/`):
- `textureEditorStore.spec.ts` (7 tests): `loadAtlas()` limpia ambas pilas;
  una sola llamada a `recordPatch()` es exactamente una unidad de Undo
  (`canUndo`/`canRedo` antes y después de un solo `undo()`); tamaño de
  `beforePixels`/`afterPixels` proporcional al `rect` (atlas de 1024×1024,
  rect de 4×4 — 64 bytes, no ~4MB); Undo aplica `beforePixels`, Redo aplica
  `afterPixels` (verificado leyendo el atlas vía `readRegion()`); un
  `recordPatch()` posterior a un `undo()` descarta la rama de Redo pendiente;
  no-ops defensivos sin atlas cargado y con pilas vacías.
- `textureEditorStore.crossStoreIndependence.spec.ts` (2 tests, AC #5, test
  cruzado explícito): un `undo()` en `textureEditorStore` no toca el modelo
  pendiente de `draftModelStore` (y viceversa) — ambas direcciones probadas
  por separado, instanciando los dos stores en el mismo test con Pinia real.

**Resultado real de la suite** (`frontend/`): `npm test` → **388 tests, 0
failures** (55 archivos, +9 nuevos de este ticket). `npx vue-tsc -b` sin
errores. `npm run lint` (`eslint . --max-warnings 0`) sin hallazgos.

**Fuera de alcance de este ticket, explícito** (no fabricado, tal como pide la
tarea): ningún canvas, ninguna herramienta de pintado (brush/fill/paste),
ninguna integración con `pointerdown`/`pointermove`/`pointerup` reales, ningún
atajo de teclado Ctrl+Z/Ctrl+Y cableado a UI — todo eso es el ticket 047, que
consumirá `loadAtlas()`/`readRegion()`/`recordPatch()`/`undo()`/`redo()` desde
su propio código de canvas.
