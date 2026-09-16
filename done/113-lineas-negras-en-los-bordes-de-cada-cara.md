# 113 — Líneas negras en los bordes de las caras texturadas

## Objetivo
Reportado por el PO sobre `Carcomido v3`: "no me gusta que haya líneas negras". Confirmado midiendo el atlas real: hay bandas de 1-2 px completamente negras pegadas a los bordes de una parte de las caras, que en el render se ven como costuras oscuras entre cuboides.

## Diagnóstico medido
Sobre 40 caras del atlas de `Carcomido v3` (umbral: columna/fila con luminancia < 8 en todos sus píxeles):

| Borde | Caras con banda negra |
|---|---|
| Izquierdo | 12 de 40 |
| Derecho | 3 de 40 |
| Superior | 16 de 40 |
| Inferior | 20 de 40 |

Ejemplo concreto: la cara `north` de la cabeza tiene sus 2 primeras columnas con luminancia 0.1 y 0.6, contra 51-94 del resto. El alpha es 255 en todas — es negro pintado, no transparencia.

**No es un desfase del recorte**: las 6 caras del torso no tienen ninguna banda, y las de la cabeza sí. Un error de coordenadas afectaría a todas por igual. Varía por sheet, o sea por imagen generada → es el modelo dejando margen oscuro DENTRO de cada región.

**Causa probable (a confirmar)**: el prompt le pide explícitamente al modelo que respete un margen sin contenido entre regiones ("separadas por un margen de Xpx sin contenido... sin invadir el de las demás regiones ni el margen entre ellas"). Un modelo prudente reserva ese margen *dentro* del rectángulo y lo pinta oscuro.

Lo irónico: ese margen existe justamente para que el bleed no contamine a la cara vecina, y el slicer ya recorta por el rect exacto — o sea que **cualquier bleed hacia afuera ya se descarta solo** (diseño del ticket 053). Pedirle al modelo que además lo reserve es pedirle dos veces lo mismo, y encima le cuesta píxeles útiles.

## Alcance
**Incluye:**
- Cambiar la instrucción del prompt: que cada rectángulo se pinte **completo, de borde a borde**, sin marcos, bordes ni márgenes internos. El gutter sigue existiendo en el layout (y el slicer sigue recortando exacto), pero deja de pedírsele al modelo.
- Verificación en vivo contra `studio-dev` con la misma medición de bandas negras, antes y después.

**No incluye:**
- Post-procesado que recorte o estire bordes negros. Es tapar el síntoma y además destruiría contenido legítimamente oscuro.
- La UV invertida del preview (ticket 112, ya resuelto).

## Criterios de aceptación
- Dado el prompt nuevo, cuando se genera una textura real, entonces la cantidad de caras con banda negra en algún borde baja de forma clara respecto de la medición de arriba (que queda como línea de base).
- Dado que la medición no mejore, cuando se concluye, entonces se documenta como limitación del proveedor y se evalúa el post-procesado en otro ticket — nunca se declara resuelto sin la medición.
- La suite existente sigue en verde (el cambio es de texto de prompt; los tests que fijan el formato del prompt se actualizan explícitamente).

## Hecho
### Cambio
El prompt dejó de pedir que el modelo reserve el margen entre regiones y pasó a pedirle lo contrario: llenar cada rectángulo **de borde a borde**, sin marcos, contornos ni márgenes internos, aclarando que lo que queda fuera se descarta. El gutter sigue en el layout del `ShelfBinPacker` y el slicer sigue recortando por el `sheetRect` exacto — la protección real contra bleed no cambió, solo se dejó de pedirla dos veces.

Test nuevo en `TextureSheetPromptComposerTest` que fija la intención (pide borde a borde, no pide reservar margen). Backend 581/581.

### Verificación en vivo: mejoró, pero NO está resuelto
Regenerada la textura de `Carcomido v3` (mismo mob, misma UV, solo cambió el prompt) y medida con el mismo criterio que la línea de base (columna/fila con luminancia ≤ 8 en todos sus píxeles):

| Métrica | Antes | Después |
|---|---|---|
| Bandas en la muestra de 40 caras (izq/der/arr/aba) | 12 / 3 / 16 / 20 = **51** | 5 / 3 / 6 / 14 = **28** |

Es una baja de ~45% en la métrica comparable. Pero midiendo el atlas COMPLETO (202 caras, dato que antes no tenía):

| Métrica (atlas completo, después) | Valor |
|---|---|
| Caras con alguna banda negra | **75 de 202 (37%)** |
| Caras con más del 50% de píxeles negros | 16 |
| Cara 100% negra | 1 |
| Promedio de píxeles negros por cara | 15,1% |

**Conclusión honesta**: el cambio de prompt ayudó y se queda (es estrictamente mejor y no tiene contraindicación), pero no alcanza. Más de un tercio de las caras sigue con banda negra, y el borde inferior casi no mejoró (20 → 14). Pedirle al modelo que llene el rectángulo reduce el problema; no lo elimina, porque depende de que obedezca.

### Consecuencia: hace falta una solución determinista
El propio alcance de este ticket anticipaba este desenlace ("si la medición no mejora... se evalúa el post-procesado en otro ticket"). Mejoró a medias, así que aplica igual: **ticket 114**, relleno determinista de bordes (dilatar el píxel válido más cercano sobre las bandas negras del borde antes de componer). Eso no depende del modelo y elimina la costura por construcción.

### Nota de método
Durante la verificación, el panel del generador se cerró y perdió la propuesta de la UI; se recuperó el `jobId` de las entradas de red de la página para medir el atlas ya aplicado, sin gastar otra tanda de llamadas.
