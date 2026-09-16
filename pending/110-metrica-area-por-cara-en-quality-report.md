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
