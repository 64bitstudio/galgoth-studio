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
### `SemanticPartCategory` (taxonomía cerrada)
- Nuevo enum de dominio con 21 valores: las 8 categorías de anatomía primaria que ya emite `HumanoidCanonicalTemplate` (HEAD/TORSO/ARM/FOREARM/HAND/LEG/SHIN/FOOT, verificadas leyendo el template real, no inventadas) + las secundarias del benchmark Carcomido (JAW, CLAW, HORN, SPIKE, TAIL, WING, EAR, EYE, TORN_CLOTH, LOINCLOTH, ARMOR, EMISSIVE_CRACK) + `GENERIC`.
- `fromRawValue(String)` normaliza mayúsculas/espacios/guiones (`"torn cloth"` = `"torn-cloth"` = `TORN_CLOTH`) y manda a `GENERIC` cualquier valor desconocido, `null` o vacío — **nunca descarta el dato** (AC). Hace falta porque `Cuboid.semanticPart` sigue siendo `String`: lo escribe el LLM de geometría secundaria y puede devolver cualquier cosa por más que el prompt liste las categorías.
- Espejo TypeScript (`SEMANTIC_PART_CATEGORIES` + tipo `SemanticPartCategory`) y enum listado en `contracts/schemas/model-intent.schema.json`.

### `ModelIntent.featureCategories` (aditivo)
- Campo **opcional** `featureCategories: SemanticPartCategory[]`, una categoría por cada `features[]` en el mismo orden. `features` **no cambia de forma** (sigue siendo la descripción libre que alimenta el prompt de geometría secundaria) — el contrato anterior queda intacto: un `ModelIntent` sin el campo sigue siendo válido contra el schema (`required` no cambió).
- El prompt de vision ahora pide las categorías explícitamente, con la lista cerrada y la instrucción de usar `GENERIC` antes que omitir o inventar. **`PROMPT_VERSION` sube de `vision-v1` a `vision-v2`** — se persiste en `ai_jobs.prompt_version`, así que los jobs viejos y nuevos quedan distinguibles (ningún test dependía del literal; verificado por grep).
- `categoriesOrDerived()` resuelve el caso "el proveedor no las devolvió" (respuestas previas, o un proveedor que ignore esa parte del prompt) derivándolas del texto libre — documentado explícitamente como **fallback**, no camino principal: solo acierta cuando la feature ES el nombre de la categoría; la categorización buena es la del proveedor, que ve la imagen. También cubre el caso de largos desalineados (categorías ≠ features): se ignoran y se derivan, nunca se emparejan mal.

### `ModelGenerationQualityReport`
- Nuevo, determinista y sin IA: `featureCoverage` (proporción de categorías detectadas con al menos un cuboid de esa misma categoría, **por igualdad de enum**), `uncoveredCategories` (lista explícita, no solo el número — AC), `semanticCoverage`, `geometryComplexity` y `fmmCompatible`.
- `Metric(available, value)`: lo que no se puede calcular se marca `unavailable` y su valor no debe leerse — **nunca un 0 que se confunda con "medido y dio cero"** (AC). Casos reales cubiertos: sin features detectadas no hay denominador para `featureCoverage`; sin cuboids no lo hay para `semanticCoverage`; si la validación FMM falla al correr, la métrica queda `unavailable`.
- Se calcula al final del pipeline y se **loguea** (no se expone en UI en v1, confirmado en el documento de definición). Reutiliza el resultado de la validación FMM que el pipeline ya corría — no la ejecuta otra vez.

### Tests
- `ModelGenerationQualityReportTest` (10, nuevo): cobertura total y parcial con lista explícita de faltantes, `unavailable` en los tres casos reales, normalización a `GENERIC` sin descartar el cuboid, normalización de mayúsculas/espacios/guiones, derivación de categorías cuando el proveedor no las manda, y desalineación de largos.
- Backend 575/575 en verde. Frontend 913/913, `vue-tsc` y `eslint` limpios.

### Cambios de contrato, señalados explícitamente (regla 9)
Ambos **aditivos y compatibles hacia atrás**, ninguno rompe consumidores existentes: (1) `model-intent.schema.json` gana la propiedad opcional `featureCategories` (no entra en `required`); (2) `ModelIntent` gana el componente con constructor de compatibilidad. El único cambio observable en datos ya persistidos es el valor nuevo de `ai_jobs.prompt_version` para jobs futuros.

### No aplica
Sin UI (el reporte es diagnóstico interno en v1, decisión ya tomada en el documento de definición) ni endpoints nuevos: no corresponde revisión visual ni colección Postman.
