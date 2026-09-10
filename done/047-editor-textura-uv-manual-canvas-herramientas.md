# 047 — Editor de textura/UV manual: canvas y herramientas

**Milestone:** M8 · **Depende de:** 002, 008, 016, 040, 046 · **HUs:** HU-24, HU-26, HU-27 · **Épica:** J (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Alcance/HU-24/26/27). Pixel editor (canvas 2D/OffscreenCanvas) sobre el atlas de textura del mob, con el layout UV ya calculado como guía visual, herramientas estándar de edición de píxeles, y preview 3D en vivo reutilizando el `ThreeViewportService` singleton ya existente. No incluye selección cruzada cuboid↔UV (ticket 049) ni import de PNG (ticket 048) — solo el canvas y sus herramientas de pintado.

## Criterios de aceptación (TDD)
- Dado un mob con geometría ya usable (`revision_number >= 1`), cuando entro al tab "Textura", entonces el canvas muestra el atlas completo con el layout UV superpuesto como guía, y un selector de región (dropdown) resalta/enfoca la región elegida.
- Dado un mob SIN ninguna revisión guardada todavía (draft en memoria), cuando entro al tab Textura, entonces carga sobre el draft vacío, nunca bloqueado — mismo criterio ya resuelto para el editor de modelo (ticket 034).
- Dado que elijo un color y pinto con el pincel, cuando el trazo se aplica, entonces usa ese color exacto sin antialiasing (pixel-perfect, verificado a nivel de píxel en el test).
- Dado que uso la Cubeta sobre una región de color contiguo, cuando hago clic, entonces se rellena esa región contigua (flood-fill estándar) — nunca fuera de sus límites de color.
- Dado que uso el Eyedropper sobre un píxel, cuando hago clic, entonces el color activo pasa a ser el de ese píxel exacto.
- Dado que cambio el tamaño de pincel/borrador, cuando pinto, entonces el trazo respeta ese tamaño en píxeles del ATLAS, no en píxeles de pantalla (independiente del zoom del canvas).
- Dado que activo el toggle de cuadrícula, cuando lo activo, entonces se superpone una grilla visual — nunca se persiste como parte de la textura exportada (test que confirma que el bitmap subido/persistido no incluye la grilla).
- Dado que aplico cualquier trazo (pincel/borrador/cubeta), cuando el trazo se completa, entonces el preview 3D (mismo viewport singleton reutilizado, sin instanciar uno nuevo) refleja el cambio de inmediato.
- Cada herramienta produce sus cambios como `TexturePatchCommand` (ticket 046) — sin excepciones.

## Hecho

Implementado el canvas real y sus herramientas de pintado sobre el mecanismo
de Undo/Redo del ticket 046 (`textureEditorStore`), con preview 3D en vivo
reutilizando `ThreeViewportService`. NO ensambla la pantalla completa del
mockup 07 (ticket 050), NO incluye selección cruzada cuboid↔UV (ticket 049)
ni import de PNG (ticket 048).

### Componente nuevo

- **`frontend/src/editor/texture/TextureCanvas.vue`**: componente único que
  reúne canvas + herramientas + preview 3D (props: `model: MobProjectModel`).
  Arquitectura de dos capas superpuestas sobre el mismo `<div>`:
  - Un `<canvas>` bitmap real, tamaño intrínseco EXACTO al atlas (nunca más),
    mostrado más grande en pantalla vía CSS (`image-rendering: pixelated`) —
    la conversión de coordenadas de puntero a píxel de atlas
    (`canvasPointToAtlas`) ya divide por el factor de escala real entre
    `canvas.width/height` y `getBoundingClientRect()`, así que el tamaño de
    pincel es correcto sin importar cuánto CSS lo agrande.
  - Un `<svg>` overlay (`pointer-events: none`) para el layout UV de guía
    (rects etiquetados por región), el resaltado de la región seleccionada y
    la cuadrícula — capa separada que NUNCA toca `atlas.pixels`, así que la
    grilla/etiquetas no pueden filtrarse al bitmap real por construcción
    (no hace falta un test que "limpie" nada antes de exportar).
- **Selector de región** (`<select>`): "Todas las caras" + una opción por
  `UvRegion` no-`orphan`, etiquetada `"${cuboid.name} (${face})"` — misma
  convención que ya usa `MobEditor.vue` (ticket 043) para el modal de
  confirmación de pérdida de pintura ("Cabeza (north)"), reutilizada en vez
  de inventar un formato nuevo. Elegir una región la resalta en el overlay
  (borde + relleno translúcido con el accent, nunca solo color — también
  cambia el grosor del borde). Riesgo #1 del documento de definición (sin
  convención fija de nombres de bone/cuboid) aceptado tal cual, como el
  propio documento anticipaba.

### Herramientas implementadas (las 6 explícitas del ticket)

Pincel, Borrador, Cubeta, Eyedropper, color picker + paleta fija de 8
colores, toggle de cuadrícula. Toda la manipulación de píxeles vive en un
módulo puro y 100% independiente de canvas/DOM:

- **`frontend/src/editor/texture/pixelTools.ts`**: `stampSquare`/`stampLine`
  (Pincel y Borrador comparten esta única función — Borrador es exactamente
  lo mismo con `TRANSPARENT` como color, cero duplicación), `computeFloodFill`
  (BFS 4-direccional real, nunca muta su entrada, devuelve `rect`/before/after
  acotados al bounding box tocado), `pickColorAt`. Deliberadamente sin
  ninguna dependencia de `<canvas>`/`getContext('2d')`: jsdom no implementa
  un contexto 2D real (`getContext('2d')` devuelve `null` sin el paquete
  nativo `canvas`, no instalado en este repo) — si el pintado dependiera de
  esa API, sería imposible escribir un test real de "pixel-perfect, sin
  antialiasing". Escribiendo bytes RGBA directo sobre el `Uint8ClampedArray`,
  todo es 100% testable sin DOM.
- **`frontend/src/editor/texture/textureRectBuffer.ts`**: `writeRectInto`/
  `readRectFrom` extraídas de `textureEditorStore.ts` (046, ya mergeado) para
  que la Cubeta reutilice la misma mecánica de slicing 2D sobre un buffer
  plano en vez de duplicarla — refactor interno sin cambiar la API pública
  del store ni sus tests existentes.
- **`frontend/src/editor/texture/colorHex.ts`**: conversión hex ↔ `RgbaColor`
  para el `<input type="color">` nativo — alfa siempre 255 (opaco); ningún
  control de opacidad parcial en este ticket (ver "Fuera de alcance" abajo).
- **`frontend/src/editor/texture/regionLabels.ts`**: construcción de las
  opciones del selector de región a partir de `MobProjectModel.uv.regions` +
  `cuboids`, excluyendo regiones `orphan` (su cuboid ya no existe, nada que
  enfocar).

### Trazo de Pincel/Borrador: un solo `recordPatch()` por trazo completo

En `pointerdown` se clona el atlas COMPLETO una única vez vía
`textureEditorStore.readRegion()` sobre el rect `{0,0,width,height}` — tal
como sugiere el propio Hecho del ticket 046 ("reutiliza `readRegion()` para
capturar `beforePixels` antes de pintar"). Ese clon (`strokeBeforeFull`) y una
copia editable (`strokeWorking`) viven SOLO durante el trazo activo; cada
`pointermove` pinta en `strokeWorking` (nunca en el atlas real todavía) y
repinta el canvas visible desde ahí, dando feedback en vivo sin tocar el
store. En `pointerup` se calcula el bounding box acumulado de todo el trazo y
se llama a `textureEditorStore.recordPatch(rect, before, after)` UNA sola vez
— clonar el atlas completo es O(atlas), pero ocurre una vez por trazo (no por
`pointermove`) y se descarta enseguida; nunca se guarda un snapshot completo
en la pila de Undo (eso sigue siendo responsabilidad exclusiva de 046, sin
cambios). La Cubeta es atómica (un solo evento) — llama a `recordPatch()`
directo con el rect que devuelve `computeFloodFill`.

### Preview 3D en vivo (HU-26) — hallazgo real no anticipado por el ticket

El ticket asumía que `ThreeViewportService`/`buildMobScene.ts` ya sabían
pintar la textura del atlas sobre los cuboids del viewport 3D. **No era
así**: `buildMobScene.ts` (ticket 008) siempre usó un `MeshStandardMaterial`
de color plano gris (`CUBOID_COLOR`), sin ningún `map` ni UV real asignada a
la geometría — el atlas nunca se había renderizado en 3D en todo el proyecto.
Sin esto, "el preview 3D refleja el cambio" (AC explícito de HU-26 de este
mismo ticket) habría sido imposible de cumplir. Corregido, de forma aditiva
y sin tocar el trabajo reservado a HU-25/ticket 049:

- **`frontend/src/viewport/textureUvMapping.ts`** (nuevo): `applyCuboidFaceUvs`
  reescribe el atributo `uv` de una `BoxGeometry` recién creada usando el
  rect real de cada `Cuboid.faces[x].uv` (ya calculado por AutoUv, ticket
  006/007) — normalizado a 0..1 contra `model.uv.textureWidth/textureHeight`.
  Usa el orden de caras conocido y determinista de `THREE.BoxGeometry`
  (+x,-x,+y,-y,+z,-z → east/west/up/down/south/north, convención Minecraft ya
  confirmada en el ADR 0001) para mapear cada grupo de 4 vértices sin tocar
  `materialIndex` ni grupos de material — eso es explícitamente HU-25/ticket
  049 ("etiquetado de cada cara/grupo de material... en `buildCuboidMesh`"),
  no se invade ese trabajo.
- **`frontend/src/viewport/buildMobScene.ts`**: `buildMobGroup`/
  `buildCuboidMesh` ganan un 3er parámetro opcional `atlasTexture` — sin él
  (todos los callers ya existentes: `ThreeViewport.vue`,
  `GenerationPreviewViewport.vue`), el comportamiento es EXACTAMENTE el
  mismo de siempre (gris plano); con él, el material usa `map: atlasTexture`
  y color blanco (para no teñir los colores reales del atlas).
- **`frontend/src/viewport/ThreeViewportService.ts`**: `setModel` forwardea
  ese 3er parámetro tal cual.
- En `TextureCanvas.vue`, la textura 3D es un `THREE.DataTexture` que
  envuelve DIRECTO el `Uint8ClampedArray` del atlas (`flipY = false`,
  `NearestFilter`, sin mipmaps — pixel-art nítido) — sin canvas 2D
  intermedio, así que no depende de `getContext('2d')` y es 100% testable.
  `threeViewportService.setModel(...)` se llama UNA vez al bindear un atlas
  nuevo (mismo buffer reutilizado en cada trazo posterior); tras cada trazo/
  fill, se llama explícitamente `dataTexture.needsUpdate = true` (ver
  siguiente hallazgo sobre por qué es una llamada explícita y no un
  `watch()`).

**Segundo hallazgo real, más sutil — `triggerRef` de un `shallowRef` de Pinia
NO dispara de forma confiable un `watch()` externo al store**: el diseño
original de este ticket asumía un único `watch(() => textureEditorStore.atlas,
...)` en `TextureCanvas.vue` como fuente de verdad para refrescar el preview
3D tras CUALQUIER cambio de atlas (trazo, fill, Undo, Redo), aprovechando que
`textureEditorStore.recordPatch()`/`undo()`/`redo()` (046) ya llaman
`triggerRef(atlas)` internamente. Verificado en vivo (reproducción mínima
aislada, sin este componente de por medio) que el PRIMER cambio de un
`shallowRef` de un store `defineStore(..., () => {...})` (una reasignación
real de `.value`, ej. `loadAtlas()`) sí notifica a un `watch()` externo — pero
una mutación in-place + `triggerRef()` posterior (exactamente el patrón de
`recordPatch()`/`undo()`/`redo()`) NO lo hace de forma confiable. Corregido
sin tocar el store (046 ya está mergeado y su contrato/tests no cambian):
`TextureCanvas.vue` llama explícitamente a `syncDataTexture()`/`redraw()`
justo después de cada `recordPatch()` que ÉL MISMO dispara (fin de trazo,
fill) — ya no depende de ningún `watch()` sobre el store para esto.
**Gap señalado, no resuelto en este ticket**: como este componente no cablea
Undo/Redo con UI (ver "Fuera de alcance"), no había forma de verificar el
mismo problema para `undo()`/`redo()` end-to-end — pero por el mismo
mecanismo, un futuro consumidor que dispare `textureEditorStore.undo()`/
`redo()` (ej. un atajo Ctrl+Z en el ticket que cablee esa UI) **debe**
refrescar la textura 3D/canvas 2D explícitamente después de llamarlos, nunca
asumir que un `watch()` externo al store lo hará solo. Recomendación de
mejora continua al cierre: documentar este gotcha de Pinia/`triggerRef` en la
memoria del equipo (mismo patrón que `ui-accessibility-guard-gotchas.md`),
para que el próximo ticket que consuma `textureEditorStore.undo()/redo()`
desde fuera no repita el mismo hallazgo a ciegas.

### Carga inicial (AC de HU-24)

`TextureCanvas.vue` llama `textureEditorStore.loadAtlas(model.uv.textureWidth,
model.uv.textureHeight)` en `onMounted` y cuando cambia `model.mobId` — sin
distinguir `revision_number >= 1` de un draft vacío en memoria: ambos casos
llegan con dimensiones ya válidas (`emptyMobProjectModel` usa 128×128 por
defecto), mismo criterio sin código especial que ya resolvió el ticket 034
para el editor de Modelo. **Decisión de diseño explícita, sin parche
silencioso**: el atlas siempre carga EN BLANCO al tamaño real — no existe
todavía ningún backend que devuelva bytes de una textura ya pintada
(`GET`/`PUT /texture` es HU-30/31, fuera de alcance de los tickets 040-046 ya
mergeados) ni ningún campo en `MobProjectModel` que transporte píxeles
inline. "Cargar el atlas real" en este ticket significa "con las dimensiones
reales del modelo", no "con el contenido ya pintado de una sesión anterior"
— eso llega cuando exista la persistencia de HU-30/31.

### Fuera de alcance de este ticket, explícito (sin parches silenciosos)

- **Selección cruzada cuboid↔UV** (HU-25/ticket 049) y **selección de
  cuboid al hacer clic en el viewport 3D**: no implementado. El mapeo UV
  agregado (`textureUvMapping.ts`) es necesario para RENDERIZAR el atlas
  correctamente, pero no incluye ningún mecanismo de picking/`materialIndex`
  — eso es 100% responsabilidad de 049, sin invadirlo.
- **Import de PNG** (HU-28/ticket 048): no implementado.
- **Selección de región rectangular / Copiar-pegar**: HU-27 los menciona
  como parte de la lista más amplia de "herramientas estándar", pero la
  sección "Qué implementar" de ESTE ticket (047) enumera explícitamente solo
  color picker/paleta, Pincel, Borrador, Cubeta, Eyedropper y toggle de
  cuadrícula — no se inventó una implementación de Selección/Copiar-pegar no
  descrita.
- **Undo/Redo con UI propia (botón/atajo Ctrl+Z)**: por el mismo motivo que
  el punto anterior — el ticket no los lista entre "las herramientas" a
  construir. El mecanismo (`textureEditorStore.undo()/redo()`, 046) queda
  disponible y cada herramienta lo alimenta correctamente vía `recordPatch()`
  (verificado con un test real de Undo revirtiendo tanto un trazo de Pincel
  como estado post-fill), pero sin botón ni atajo de teclado cableado en
  este componente. Ver el gap señalado arriba sobre refrescar el preview 3D
  si un ticket futuro cablea esto.
- **Control de opacidad**: mencionado en el mockup 07/HU-41 (layout de la
  PANTALLA completa, ticket 050) pero no en la lista de herramientas de
  HU-27/este ticket — no implementado. Pincel/Cubeta pintan con alfa 255,
  Borrador escribe alfa 0.
- **Zoom interactivo del canvas**: no implementado, dicho explícitamente. El
  canvas se muestra más grande que su resolución intrínseca vía una escala
  CSS FIJA (no un control de zoom interactivo) — la conversión de
  coordenadas de puntero ya tiene en cuenta esa escala tal como lo
  necesitaría un zoom real, y el test de "tamaño de pincel en píxeles del
  atlas" se verifica exactamente con ese canvas escalado (CSS 10x más
  grande que su resolución real), cumpliendo la letra del AC sin construir
  una UI de zoom.
- **Ensamblado de la pantalla completa (mockup 07, tabs Modelo/Textura)**:
  ticket 050, no tocado — `TextureCanvas.vue` es la pieza que ese ticket va
  a montar en el tab "Textura" (hoy deshabilitado).
- **Revisión visual en vivo con Claude in Chrome**: **NO disponible en este
  entorno** (worktree aislado sin extensión de Chrome conectada —
  `list_connected_browsers` devolvió una lista vacía, verificado en esta
  misma sesión). Verificado únicamente con el chequeo estático de
  accesibilidad de cada commit (hook `ui-accessibility-guard`) y con la
  suite de tests real. Queda pendiente una pasada visual contra la app
  corriendo antes de considerar la UI 100% validada — mismo gap ya señalado
  en el ticket 043.

### Tests

`frontend/src/editor/texture/__tests__/`: `pixelTools.spec.ts` (13 tests —
pincel/borrador pixel-perfect sin antialiasing, tamaño exacto del trazo,
recorte contra bordes del atlas, flood-fill dentro de límites de color sin
cruzar bordes, no-op si el color ya es el vigente, eyedropper exacto),
`colorHex.spec.ts` (2), `regionLabels.spec.ts` (4 — etiqueta
`"cuboid (face)"`, exclusión de regiones `orphan`), `TextureCanvas.spec.ts`
(13 — carga inicial con dimensiones reales, selector de región + resaltado,
pincel pixel-perfect con UN solo `recordPatch()` por trazo, borrador
comparte el mecanismo con alfa 0, tamaño de pincel en píxeles de atlas
verificado con el canvas escalado 10x por CSS, cubeta con un solo
`recordPatch()`, eyedropper sin registrar ningún Command, grid nunca cambia
un byte del atlas, preview 3D con el MISMO objeto `Texture`/buffer tras un
trazo, Undo revierte un trazo real, a11y de todos los controles).
`frontend/src/viewport/__tests__/textureUvMapping.spec.ts` (4 tests) +
casos nuevos agregados a `buildMobScene.spec.ts`/`ThreeViewportService.spec.ts`
(atlasTexture forwardeado correctamente, comportamiento sin cambios cuando
no se pasa).

**Resultado real de la suite** (`frontend/`): `npx vitest run` → **441
tests, 0 failures** (62 archivos, +53 nuevos de este ticket). `npx vue-tsc -b`
sin errores. `npx eslint --max-warnings 0 .` sin hallazgos. `npx vite build`
en verde.
