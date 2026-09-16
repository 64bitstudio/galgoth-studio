# 110 — Métrica de área por cara en `ModelGenerationQualityReport` + assertions en el benchmark

## Objetivo
Nace de `docs/definiciones/densidad-de-texel-y-resolucion-de-textura.md` (HU-4), con VoBo del PO. El problema de fondo (caras de 1-2 téxeles) se descubrió midiendo a mano contra la API en una sesión de diagnóstico. Esa medición debe vivir en el reporte de calidad para que una regresión de este tipo se vea sola, en vez de depender de que alguien mire un mob y le parezca feo.

**Depende de:** 109 (mide su efecto) y 104 (`ModelGenerationQualityReport` ya existe).

## Alcance
**Incluye:**
- `ModelGenerationQualityReport` gana la distribución de área UV por cara: mínimo, mediana, percentil 75 y cantidad de caras por debajo del mínimo legible (16 px²), calculadas sobre las caras NO degeneradas.
- Las caras degeneradas (área cero, hallazgo real del ticket 064) se excluyen del cálculo y se cuentan aparte — nunca inflan ni ensucian la métrica.
- El benchmark Carcomido (105) verifica que ninguna cara no degenerada queda bajo 16 px².
- La línea de log del reporte incluye la métrica nueva.

**No incluye:**
- Exponer el reporte en la UI (sigue siendo diagnóstico interno, decisión ya tomada).

## Criterios de aceptación (TDD)
- Dado un modelo con caras de distintos tamaños, cuando se calcula el reporte, entonces expone mínimo, mediana, p75 y conteo de caras bajo 16 px².
- Dado un modelo con caras degeneradas, cuando se calcula la métrica, entonces esas caras se cuentan aparte y no participan de los percentiles.
- Dado un modelo sin ninguna cara con UV, cuando se calcula, entonces la métrica se marca `unavailable` — nunca un cero inventado (mismo criterio del 104).
- Dado el benchmark Carcomido corriendo a la densidad del 109, entonces ninguna cara no degenerada queda bajo 16 px².

## Hecho
### La métrica encontró un bug real en su primera corrida
El ticket era "agregar una métrica". En cuanto el benchmark la usó como assertion, **falló**: 44 caras bajo 16 px² y `min=1px²` en un modelo que se suponía generado a X4. Un cuboid de 1×1×1 daba caras de 1×1 téxel — números de X1, no de X4.

Causa: el ticket 109 conectó la densidad hasta `AlphaAutoPackStrategy`, pero **el bean que realmente se inyecta en producción es `UvLayoutSelector`** (`@Primary`), que no sobreescribía la sobrecarga con densidad. El `default` de la interfaz delegaba en la versión sin densidad y el packing volvía silenciosamente a X1. Los tests unitarios del 109 pasaban porque construyen `AlphaAutoPackStrategy` directamente — el camino real de Spring nunca se ejercitaba.

O sea: **la cadena tenía dos eslabones rotos, no uno**. El 109 arregló el de abajo y este destapó el de arriba. Corregido: `UvLayoutSelector` propaga la densidad a la estrategia que elige.

Antes y después, mismo benchmark:

| | antes | después |
|---|---|---|
| área mínima por cara | 1 px² | **16 px²** |
| mediana | 16 px² | **256 px²** |
| p75 | 36 px² | **576 px²** |
| caras bajo el mínimo legible | 44 | **0** |

### Implementado
- `ModelGenerationQualityReport.FaceAreaStats`: mínimo, mediana, p75, cantidad de caras bajo `MINIMUM_LEGIBLE_PX2` (16 px² = 4×4) y cantidad de caras degeneradas **contadas aparte** — las de área cero (hallazgo real del 064) no participan de los percentiles, porque incluirlas hundiría la mediana y haría ver como problema un caso esperado.
- Sin ninguna cara con área real, la métrica queda `null` y `describe()` dice "no disponible" — mismo criterio que el resto del reporte (104): nunca un cero inventado.
- `UvLayoutSelector` propaga la densidad (el fix de arriba).
- El benchmark Carcomido falla si alguna cara no degenerada queda bajo el mínimo legible.

### Tests
- `ModelGenerationQualityReportTest` (+3): distribución con conteo de ilegibles, caras degeneradas contadas aparte sin hundir percentiles, y métrica no disponible sin UV.
- `CarcomidoBenchmarkTest`: assertion nueva sobre `facesBelowMinimumLegible`, con el mensaje incluyendo mín/mediana para que un fallo futuro se lea sin depurar.
- Backend 580/580 en verde.

### Nota de método
El diagnóstico temporal que se usó para encontrar la causa (imprimir cuboid + cara + téxeles de cada región ilegible) se eliminó del test antes de commitear — no queda ruido de depuración en la suite.
