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
