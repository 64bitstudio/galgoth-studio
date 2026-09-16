# 112 — La UV del preview 3D está invertida verticalmente

## Objetivo
Reportado por el PO mirando `Carcomido v3`: "la cara de la textura está más o menos definida pero en el render 3D está volteada". Confirmado: **todas las caras de todos los cuboides se renderizan espejadas verticalmente** en el preview 3D. El bug es viejo; recién ahora se puede ver, porque hasta el ticket 109 las texturas eran ruido y un ruido dado vuelta se ve igual que el ruido derecho.

## Diagnóstico (con evidencia, no por lectura)
1. En el atlas real de `Carcomido v3`, la cara `north` de la cabeza (rect `[132,36,168,72]`) tiene una cara de personaje bien formada: franjas violetas brillantes (ojos) en el tercio SUPERIOR y formas claras verticales (dientes) ABAJO.
2. El render 3D muestra ese mismo contenido al revés.
3. Causa en `textureUvMapping.ts`: la textura se sube como `DataTexture` con `flipY = false` (`TextureCanvas.vue`) y su buffer sale de `drawImage` + `getImageData`, o sea fila 0 = arriba de la imagen. Con `flipY=false`, `v=0` muestrea esa fila 0. Pero los vértices SUPERIORES de cada cara de una `BoxGeometry` traen `v=1` por defecto, y `applyCuboidFaceUvs` les asignaba `v1` (el borde INFERIOR del rect). Resultado: arriba de la cara se muestra abajo del rect.
4. El propio Javadoc de `normalizeFaceUv` ya declaraba la intención correcta ("la fila 0 del buffer (arriba en pixel-space) corresponda a v=0"); la implementación no la cumplía.

**Por qué ningún test lo detectó**: los tests existentes verificaban que las UV cayeran en el cuadrante correcto y que las caras no se mezclaran entre sí, pero **ninguno fijaba la orientación vertical**.

## Alcance
**Incluye:**
- Corregir la asignación en `applyCuboidFaceUvs`.
- Test que fije la orientación vertical (el que faltaba).

**No incluye:**
- El `.bbmodel` exportado, que NO está afectado: el export escribe los rects de `Face.uv`, que siempre estuvieron bien. Esto es mapeo del visor, no de los datos.
- Las líneas negras en los bordes de las caras, que el PO reportó en el mismo mensaje — es otra causa, otro subsistema (ticket 113).

## Criterios de aceptación (TDD)
- Dado un rect de cara con `y0` arriba e `y1` abajo, cuando se aplican las UV, entonces los vértices superiores reciben `y0` y los inferiores `y1`.
- Dado el test anterior, cuando se corre contra el código previo a este ticket, entonces falla (se verificó: devolvía 0.5 donde se esperaba 0.1).
- Suite de frontend en verde.

## Hecho
- `applyCuboidFaceUvs`: los vértices superiores (`dv=1`) ahora reciben `v0` y los inferiores `v1`, con el porqué explicado en el propio código.
- Test nuevo en `textureUvMapping.spec.ts` que fija la orientación vertical. **Se verificó primero que fallara** contra la implementación anterior (esperaba 0.1, recibía 0.5) — un test de orientación que pasa siempre no habría probado nada.
- Frontend 914/914 en verde; ningún test dependía del comportamiento espejado.
- Falta la confirmación visual contra `studio-dev` tras el deploy: el test fija el contrato de UV, pero que el personaje se vea derecho se ve mirándolo.
