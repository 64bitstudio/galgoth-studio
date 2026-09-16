# 115 — El editor de textura abre con el atlas vacío aunque el mob tiene textura persistida

## Objetivo
Al entrar a la pestaña **Textura** de un mob que ya tiene textura aplicada, el editor muestra el atlas completamente vacío (solo el damero de transparencia y la grilla de regiones UV). La textura no se perdió: el modelo sigue referenciando su PNG. Es el editor el que no la carga.

Reproducido dos veces sobre `Carcomido v3` en `studio-dev` (`mobId 282e74d7-4f54-4e8f-8617-f332086e821f`), incluyendo después de un reload limpio de la página. En ese estado, `GET /api/mobs/{mobId}/draft` devuelve `model.texture.storageKey = "textures/6237734c…png"` — o sea el dato está, el lienzo no.

El impacto es de confianza, no solo cosmético: el usuario abre el editor y ve su trabajo perdido. Además, cualquier pincelada o import sobre ese lienzo vacío corre el riesgo de escribir encima de una textura que el usuario cree que ya no está.

## Alcance
**Incluye:**
- Diagnosticar en qué eslabón se corta: si el frontend no pide el PNG del `storageKey`, si lo pide y falla, o si lo recibe y no lo pinta en el canvas del editor.
- Que el editor cargue y muestre el atlas persistido al abrir la pestaña.
- Determinar si al guardar desde ese estado vacío se pisa la textura buena (si se pisa, es lo más urgente del ticket).

**No incluye:**
- Rediseñar el editor de textura.
- El problema de las caras chicas que salen sin contenido (otro subsistema, ver el hallazgo 1 del `done/114`).

## Criterios de aceptación (TDD)
- Dado un mob cuyo `model.texture.storageKey` apunta a un PNG con contenido, cuando se abre la pestaña Textura, entonces el lienzo muestra ese PNG y no un atlas vacío.
- Dado ese mismo mob, cuando se recarga la página estando en la pestaña Textura, entonces el lienzo sigue mostrando el PNG (el bug se reproduce sobre todo después del reload).
- Dado un mob que efectivamente NO tiene textura (sin `storageKey`), cuando se abre la pestaña, entonces el lienzo vacío es el resultado correcto y no se rompe nada.
- Verificación en vivo contra `studio-dev` sobre `Carcomido v3`, que es donde está reproducido.

## Hecho

### La causa, medida y no deducida
Midiendo en vivo contra `studio-dev`, el resultado fue inequívoco: el store del editor terminaba con el atlas **completo** (256×1024, 47.968 píxeles opacos, exactamente los mismos que el PNG persistido) y el `<canvas>` con **cero**. Los datos cargaban perfecto; lo que nunca ocurría era el pintado.

El `<canvas>` se dimensiona desde el store (`:width="atlasWidth"`), y `loadModelAtlas` llamaba a `redraw()` de forma **síncrona** justo después de `loadAtlas()`, antes de que Vue aplicara esa actualización. En ese instante el canvas todavía mide 0×0: `putImageData` se recorta a nada y **no lanza**, y acto seguido Vue le asigna el tamaño real — lo que en un canvas **resetea el bitmap a transparente**. Nada vuelve a pintar.

Eso explica el síntoma completo, incluida la parte que parecía incoherente: la textura **sí** se veía al aplicar una generada por IA (el canvas ya estaba dimensionado por una carga anterior) y desaparecía al recargar. También explica por qué el bug es "transitorio": cualquier repintado posterior (un resize, un zoom, un trazo, volver a entrar a la pestaña) lo corrige solo, lo que lo hace fácil de no reproducir.

Arreglo: `await nextTick()` antes de `redraw()`. El `saveState = 'saved'` se fija **antes** del await a propósito — un atlas recién cargado es igual a lo persistido, y diferirlo hacía que aterrizara después de un trazo del usuario pisándole el "Cambios sin guardar". Lo detectaron dos tests del 058; no se descubrió de casualidad.

### Una hipótesis previa, descartada al medir
Antes de medir supuse un desajuste entre las dimensiones que declara el UV y las del PNG — con mecanismo plausible: `GeometryPlannerService.withInitialAtlas` reescala el atlas al regenerar geometría con otra densidad y **conserva el `storageKey` anterior**. Al medir, las tres dimensiones coincidían (modelo 256×1024, UV 256×1024, PNG 256×1024). **La hipótesis era falsa.** Queda anotado porque el error de método es el mismo que había cometido en el 114: corregir donde creo que está el defecto en vez de donde se lo mide.

### Respuesta a la pregunta abierta del ticket: sí, se pisaba la textura buena
Era el punto más urgente y la respuesta es afirmativa, por tres caminos, hoy cerrados con un test cada uno:
- **Desajuste de dimensiones** entre el PNG y lo que declara el modelo: ahora da un error visible con los dos tamaños en vez de armar un atlas incoherente.
- **`storageKey` presente pero el backend responde 404**: era lienzo en blanco **en silencio**, con "Guardar" habilitado. Justo el escenario que sobrescribe.
- **El peor, y el que no estaba en el ticket**: el store de textura es global y no se resetea al desmontar el componente. Deshabilitar "Guardar" en la pestaña Textura no alcanzaba, porque el **"Guardar" de la pestaña Modelo** (`EditorToolbar.vue`) no conoce `atlasLoadError` — con el atlas anterior todavía en el store, subía **la textura de otro mob** como textura de este. Ahora una carga fallida **vacía** el store, y `flushPaintedTexture` no sube nada sin atlas: el camino queda cerrado desde las dos pestañas.

### Verificación en vivo (criterio de aceptación)
Misma prueba, antes y después del deploy, sobre `Carcomido v3` con recarga completa de la página (el bug solo se manifiesta en la primera carga, cuando el store arranca vacío):

| | store (datos) | canvas (lo que se ve) |
|---|---|---|
| Antes | 65,1 % opaco en la banda superior | **0 %** |
| Después | 65,1 % | **65,1 %** |

Total tras el fix: **canvas 47.968 = store 47.968**, coincidente banda por banda en las 8 bandas del atlas, sin banner de error. Confirmado además visualmente: la textura se ve en el editor.

### Hallazgo nuevo, ticketeado aparte (`pending/117`)
Con el 115 ya desplegado, el canvas 2D pinta bien pero **el modelo 3D no muestra la textura**: el preview de la pestaña Textura se ve **negro** (y no cambia tras una segunda carga, así que no es esta carrera) y el viewport de la pestaña Modelo se ve **gris plano**. Que sean distintos entre sí es la pista. No lo metí dentro de este ticket: es otro subsistema y merece su propio diagnóstico.

### Tests
4 tests nuevos en `TextureCanvas.spec.ts`, **todos verificados primero en rojo**: el de la causa raíz registra sobre qué dimensiones se pinta (exigía 4×4, recibía 0×0), y uno por cada camino de sobrescritura. Frontend completo: **918/918 en verde**. CI verde en el PR #174.
