# 104 — `SemanticPartCategory` (enum compartido) + `ModelGenerationQualityReport` con `featureCoverage`

## Objetivo
Nace de `docs/definiciones/anatomia-por-capas-generacion-mobs.md` (HU-5b, Decisión 5b). Para medir objetivamente si un personaje complejo mejora (no solo "a ojo"), se necesita comparar qué características detectadas en la referencia terminaron representadas en el modelo — pero sin depender de comparar strings libres entre `ModelIntent.features` y `Cuboid.semanticPart`. Este ticket introduce el catálogo cerrado `SemanticPartCategory` y amplía `ModelGenerationQualityReport` con `featureCoverage`.

**Depende de:** 099 (`semanticPart` ya debe existir en `Cuboid`). **Bloquea:** 105 (el reporte de calidad es parte de lo que valida el fixture Carcomido).

## Alcance
**Incluye:**
- `SemanticPartCategory`: enum compartido (backend Java, TypeScript, listado en `contracts/schemas/`) — catálogo inicial cubriendo las categorías del benchmark Carcomido (garras, cuernos/protrusiones, ropa desgarrada, mandíbula, grietas emisivas, etc.) + `GENERIC` como valor de escape. Extensible de forma aditiva.
- `ModelIntent`: cada feature detectada por vision se categoriza contra este enum (además de mantener su descripción libre para contexto humano).
- `Cuboid.semanticPart` toma valores de este mismo enum.
- `ModelGenerationQualityReport.featureCoverage`: proporción de categorías detectadas que tienen al menos un cuboid secundario con `semanticPart` de esa misma categoría — comparación por igualdad de enum, nunca fuzzy matching de texto. Categorías sin cobertura quedan listadas explícitamente (no solo como número agregado).
- Métricas ya previstas en el documento (`semanticCoverage`, `geometryComplexity`, `fmmCompatibility` reutilizado) se consolidan en el mismo reporte — cualquier métrica no calculable se marca `unavailable`, nunca se inventa.

**No incluye:**
- Exposición del reporte en UI — queda como diagnóstico interno en v1 (confirmado en el documento de definición).

## Criterios de aceptación (TDD)
- Dado que `ModelIntent` detecta una feature, cuando se registra, entonces se le asigna una categoría de `SemanticPartCategory`.
- Dado que `Cuboid.semanticPart` toma valores del mismo enum, cuando se calcula `featureCoverage`, entonces la cobertura se determina por igualdad de categoría enum.
- Dado un `ModelIntent` con features categorizadas, cuando termina la generación, entonces el reporte incluye `featureCoverage` con la lista explícita de categorías sin cobertura.
- Dado que vision detecta algo que no encaja en ninguna categoría existente, cuando se registra, entonces se usa `GENERIC` sin descartar la feature.
- Dado que una métrica no se puede calcular en un caso dado, cuando se genera el reporte, entonces se marca `unavailable`, nunca un valor inventado.

## Hecho
