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

### Qué se implementó
- **`TextureEdgeFiller`** (nuevo, determinista, sin IA): detecta columnas/filas del borde de una cara que estén completamente por debajo del umbral de negro (luminancia Rec. 601 ≤ 8, el mismo con el que se midió el problema en el 113) y las rellena con el píxel válido contiguo hacia adentro. Horizontal antes que vertical, en ese orden explícito, para que las esquinas tomen el valor ya corregido y no el negro original.
- **No toca lo que no debe**, por diseño y con test por cada caso: el negro del INTERIOR de la cara se conserva (puede ser una grieta o una sombra legítima); una cara sin bandas queda byte a byte idéntica; una banda transparente no se confunde con negro (la transparencia es un estado válido del atlas, no una costura); una banda de más del 25% del lado NO se rellena y se cuenta aparte.
- **Corre sobre el atlas ya compuesto**, una vista por cara acotada a su `atlasUvRect` — no sobre el slice. Ver abajo por qué: no fue el diseño original, fue una corrección forzada por la medición.

### El error que la verificación en vivo destapó
La primera versión rellenaba el slice **antes** de componerlo. Medido contra `Carcomido v3`, bajó las caras con banda de 75/202 a 39/202 — pero dejó **20 caras con banda estrecha de 1-2 px sobre 16 (12,5%)**, justo las que el umbral tenía que haber agarrado. Eso no era el umbral funcionando: era el relleno sin llegar.

Causa: `TextureSheetSlicer` recorta contra el tamaño REAL que se le pide a la API, así que una cara que en el atlas mide 16×16 llega al filler midiendo ~100×100 px. A esa resolución el filo oscuro es un **degradado**, no una columna uniformemente negra, y por eso no calificaba como banda. Recién cuando `TextureCompositorService` lo reduce con bilinear al rect del atlas, ese degradado colapsa en una columna negra sólida. **El defecto nace en el reescalado, o sea después del punto donde se lo estaba corrigiendo.** Que igual bajara de 75 a 39 lo confirma: las bandas que ya eran negro plano a resolución inflada sí se rellenaban.

Corregido en un commit aparte (PR #172): el relleno pasó a correr después de componer. Efecto colateral bueno: los umbrales quedan expresados en píxeles de atlas, que es la unidad en la que este ticket define el problema y en la que se lo mide — antes querían decir otra cosa que lo que el ticket dice.

### Verificación en vivo (criterio de aceptación explícito)
Regeneración completa de `Carcomido v3` en `studio-dev`, medida sobre las 202 regiones UV del atlas con el mismo criterio que la línea de base del 113:

| | 113 (solo prompt) | 114 sobre el slice | 114 sobre el atlas |
|---|---|---|---|
| Caras con banda | 75 / 202 | 39 / 202 | 35 / 202 |
| — de ellas, banda estrecha (el defecto) | — | **20** | **0** |
| — banda ancha (saltada a propósito) | — | 18 | 13 |
| — cara enteramente negra (sin píxel válido) | — | 1 | 22 |
| Muestra de 40, bandas izq/der/arr/aba | 5/3/6/14 | 2/1/0/6 | **0/0/0/5** |

**El defecto que este ticket ataca quedó en 0 de 202.** Las 35 que siguen contando "con banda" son enteramente las dos categorías que el ticket decidió NO rellenar.

### Hallazgos que el Product Owner debería saber
1. **22 caras salieron enteramente negras (en el run anterior era 1), y el promedio de negro por cara SUBIÓ de 12,7% a 17,3%.** No es una regresión del relleno — el relleno no puede ennegrecer nada, y correctamente se niega a tocar una cara sin un solo píxel válido del que copiar. Las 22 son caras **chicas**: 12 de 4×4, ninguna mayor a 16×8. Son las caras diminutas que ya venían sin contenido utilizable, el mismo problema que la rotura de `BoxUvMath.boxSizeAxis` (redondea a unidades enteras ANTES de multiplicar por la densidad). El salto de 1 a 22 entre dos corridas es grande y no puedo atribuirlo con confianza a varianza del generador con una sola medición de cada lado — pero sí puedo afirmar que no sale de este cambio.
2. **Desvío de alcance, explícito**: el ticket pedía que la métrica quedara en el reporte de calidad del 110. Quedó como log del job, no en `ModelGenerationQualityReport`, porque ese reporte se calcula en el pipeline de GEOMETRÍA y no tiene forma de saber qué pasó al texturizar (mismo criterio ya vigente para las advertencias de `TextureContentValidator`). Llevarlo al reporte de verdad implica un canal de advertencias de textura que hoy no existe — es el alcance del 104, no de este. **Queda a decisión tuya si se abre ticket.**

### Tests
9 tests nuevos en `TextureEdgeFillerTest`, uno por cada criterio de aceptación más los dos que cubren el riesgo real de correr sobre el atlas (la vista comparte raster con el padre): que lo escrito llegue al atlas y que no se derrame un píxel sobre la cara vecina, y que una cara con vecina negra se rellene con su propio color. Backend completo: **590/590 en verde**. CI verde en los PR #171 y #172.
