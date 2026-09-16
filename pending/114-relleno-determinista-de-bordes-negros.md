# 114 — Relleno determinista de los bordes negros de cada cara

## Objetivo
El ticket 113 atacó las líneas negras por el lado del prompt y lo midió: bajaron ~45% en la muestra comparable, pero **37% de las caras (75 de 202) sigue con banda negra** y el borde inferior casi no mejoró. Pedirle al modelo que llene el rectángulo depende de que obedezca, y no obedece de forma confiable. Este ticket lo resuelve sin depender de él.

**Depende de:** 113 (su medición es la línea de base y su conclusión, la justificación).

## Alcance
**Incluye:**
- Al recortar cada cara (`TextureSheetSlicer`) o antes de componerla (`TextureCompositorService`): detectar las columnas/filas del BORDE que estén completamente por debajo del umbral de negro y rellenarlas con el píxel válido más cercano hacia adentro (dilatación de 1 píxel hacia el borde, repetida hasta cubrir la banda).
- Umbral y ancho máximo de banda a rellenar explícitos y acotados: si la banda negra es ancha (p. ej. más del 25% de la cara), NO se rellena — eso ya no es una costura, es una cara que salió mal, y taparla sería esconder el problema.
- Métrica: el reporte de calidad (110) registra cuántas caras necesitaron relleno, para que se vea si el proveedor empeora con el tiempo.

**No incluye:**
- Volver a tocar el prompt (113 ya llegó hasta donde podía).
- Rellenar contenido legítimamente oscuro del interior de la cara — solo bandas pegadas al borde.

## Criterios de aceptación (TDD)
- Dada una cara con una banda negra de 1-2 px en un borde, cuando se procesa, entonces esa banda toma el color del píxel válido contiguo y deja de ser negra.
- Dada una cara con una banda negra ancha (más del umbral), cuando se procesa, entonces NO se rellena y se registra en el reporte de calidad.
- Dada una cara sin bandas negras, cuando se procesa, entonces queda byte a byte idéntica — el relleno nunca toca una cara sana.
- Dada una cara con negro legítimo en el interior (no pegado al borde), cuando se procesa, entonces ese negro se conserva.
- Verificación en vivo: regenerar `Carcomido v3` y medir contra los números del 113 (75 de 202 con banda, 15,1% de píxeles negros promedio).

## Hecho
