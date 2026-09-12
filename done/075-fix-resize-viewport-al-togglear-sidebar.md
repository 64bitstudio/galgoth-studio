# 075 — Fix: el viewport 3D (Modelo/Textura) se rompía al abrir/cerrar el sidebar

## Objetivo
Corregir un bug real reportado por el PO con capturas: al expandir el sidebar (`GSidebar.vue`) mientras se estaba en la tab "Textura" del editor de mob, el preview 3D quedaba recortado/desalineado, con un hueco muerto entre el lienzo de textura y el preview. Pedido explícito: arreglar Textura, y si el fix toca código compartido con Modelo, homologarlo ahí también.

## Diagnóstico
La causa real NO estaba en el layout CSS de `TextureCanvas.vue` (ese layout ya era correcto -- flex + `min-width:0` en los lugares correctos). Estaba en `ThreeViewportService.ts`, el singleton de renderer/canvas de Three.js que comparten `ThreeViewport.vue` (Modelo), `TextureCanvas.vue` (preview 3D de Textura) y `GenerationPreviewViewport.vue`:

- `resizeToContainer(container)` llama `renderer.setSize(width, height, false)` -- el `false` (`updateStyle: false`) es intencional (documentado en el propio código), pero como efecto secundario significa que Three.js NUNCA toca `canvas.style.width/height`. El tamaño visual del `<canvas>` en pantalla queda gobernado por sus atributos HTML `width`/`height` (fijados por `setSize`), no por CSS -- como un `<img width height>` sin `width:100%` de por medio.
- `resizeToContainer` solo se invocaba en 2 momentos: al montar (`attachTo`) y al arrastrar el splitter del preview 3D en Textura (`handleSplitterPointerMove`/`handleSplitterKeydown`). **Nunca** se invocaba cuando el contenedor cambiaba de tamaño por CUALQUIER OTRA razón -- como `GSidebar.vue` expandiéndose/colapsándose, que angosta/ensancha `.mob-editor`/`.texture-canvas` sin que nada dentro del servicio se enterara.
- Resultado: el canvas quedaba con el tamaño (atributos HTML) del último resize real, desincronizado del tamaño real (CSS) de su contenedor apenas el sidebar cambiaba -- visualmente recortado o con espacio muerto alrededor, según si el contenedor había crecido o encogido desde el último resize real.

`ThreeViewport.vue` (Modelo) tiene el MISMO problema estructural (tampoco tenía ningún mecanismo de resize reactivo) -- no se manifestaba en las capturas del PO simplemente porque no se probó esa combinación específica ahí, no porque el código fuera distinto.

## Alcance

### Incluye
- **Fix en el servicio compartido** (`ThreeViewportService.ts`), no en cada componente consumidor -- corrige Modelo, Textura y el preview de generación IA de una sola vez, sin duplicar lógica:
  - `attachTo(container)` ahora crea un `ResizeObserver` sobre `container` y llama `resizeToContainer` automáticamente ante CUALQUIER cambio de tamaño futuro (sidebar, ventana, splitter, lo que sea) -- ya no depende de que cada pantalla recuerde llamarlo a mano.
  - Reatachear a otro contenedor desconecta el observer del contenedor viejo antes de crear el nuevo (nunca observa dos contenedores a la vez).
  - `detach()` desconecta el observer (sin fugas cuando la pantalla se desmonta).
- **Setup global de tests** (`src/test-setup.ts`, nuevo, cableado en `vite.config.ts`): jsdom no implementa `ResizeObserver` -- un fake mínimo e inerte, igual de simple que el fake ya usado para `WebGLRenderer`. Un solo lugar en vez de repetir el mismo polyfill en los ~7 archivos de test que montan algo que llama `attachTo`.

### No incluye
- Ningún cambio visual/de layout CSS -- el layout de `TextureCanvas.vue` ya era correcto, el bug era 100% de sincronización del canvas WebGL con su contenedor.
- Ningún cambio de comportamiento del splitter arrastrable de Textura (sigue funcionando igual; sus llamadas manuales a `resizeToContainer` quedan, ahora simplemente redundantes con el `ResizeObserver` -- inofensivo, no se tocaron para minimizar el diff sobre código más delicado de arrastre).

## Criterios de aceptación (TDD)
- `attachTo` observa el contenedor dado; un resize del contenedor SIN llamar `resizeToContainer` a mano resincroniza `renderer`/cámara igual.
- `detach` desconecta el observer -- un resize posterior ya no toca el renderer.
- Reatachear a otro contenedor desconecta el observer del contenedor viejo antes de observar el nuevo.
- Verificado en vivo: togglear el sidebar ESTANDO YA en Textura (y en Modelo) resincroniza el preview 3D sin recortes ni huecos, en ambas direcciones (colapsar y expandir).
- Suite completa (frontend) en verde, sin hallazgos nuevos de lint/type-check, build sin errores.

## Hecho

Implementado y verificado en vivo.

- `ThreeViewportService.ts`: `attachTo`/`detach` cablean/desconectan un `ResizeObserver` real sobre el contenedor attacheado (ver diagnóstico arriba para el porqué exacto).
- `src/test-setup.ts` (nuevo) + `vite.config.ts` (`test.setupFiles`): fake global de `ResizeObserver` para jsdom -- sin él, 7 archivos de test (`ThreeViewportService`, `ThreeViewport`, `TextureCanvas`, `GenerationPreviewViewport`, `GenerationStep`, `ResultStep`, `MobEditor`, `AiMobWizard`) fallaban con `ReferenceError: ResizeObserver is not defined` en cuanto se montaba cualquier componente que llama `attachTo`.
- `ThreeViewportService.spec.ts`: 3 tests nuevos -- observa el contenedor y resincroniza ante un resize sin llamada manual, `detach` desconecta y deja de resincronizar, reatachear a otro contenedor desconecta el observer viejo.
- **Verificación en vivo** (stack real): togglear el sidebar estando ya en la tab Textura -- colapsar y expandir, ambas direcciones -- el lienzo de textura y el preview 3D se reajustan correctamente, sin recorte ni espacio muerto (el bug original reproducido y confirmado corregido). Mismo toggle repetido en la tab Modelo -- el viewport principal también se reajusta correctamente (homologación confirmada, no solo en el código).
- Estado final: 715/715 tests frontend en verde, `vue-tsc -b`/`eslint --max-warnings 0` sin hallazgos, `npm run build` sin errores.

**Gate de autorización del requerimiento** (mismo criterio que tickets anteriores): implementación LOCAL únicamente -- sin commits, sin push, sin ramas, sin PR. Ticket queda en `in-process/` hasta que el PO revise el resultado en vivo y autorice el flujo git normal.
