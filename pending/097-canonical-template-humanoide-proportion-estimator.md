# 097 — Catálogo CanonicalTemplate (humanoide) + ProportionEstimator determinista

## Objetivo
Nace de `docs/definiciones/anatomia-por-capas-generacion-mobs.md` (HU-1, HU-3). Hoy toda la anatomía de un mob generado sale de un prompt libre al LLM, sin ningún andamiaje de código — de ahí resultados genéricos tipo "Steve" para referencias complejas. Este ticket construye el primer eslabón determinista: un catálogo de templates canónicos por `baseType` (empezando por humanoide completo) y un estimador de proporciones que ajusta ese template dentro de rangos válidos según lo que `ModelIntent` detecte.

**Depende de:** nada de esta epic (es la primera pieza). **Bloquea:** 098 (usa el template ajustado que este ticket produce).

## Alcance
**Incluye:**
- Nuevo paquete `domain/template/` (backend): `CanonicalTemplate` — jerarquía de bones (head/torso/pelvis, brazos con antebrazo/mano, piernas con espinilla/pie) + rangos de proporción mín/máx por bone, para `baseType=humanoid`.
- Catálogo extensible por datos, no por rama de código: agregar un template nuevo no debe requerir tocar `GeometryEngine`.
- `ProportionEstimator`: función pura, sin red, que toma `CanonicalTemplate(baseType)` + `ModelIntent` y devuelve un template ajustado (proporciones dentro de rango) + `generationWarnings` si algo se clampeó.
- Para `baseType` distinto de humanoide: template mínimo genérico (root+body) — el resto queda para iteración 2 (fuera de alcance).

**No incluye:**
- Templates completos para arachnid/quadruped/flying (iteración 2, explícitamente fuera de alcance según VoBo del documento de definición).
- Generación de geometría en sí (eso es el ticket 098).

## Criterios de aceptación (TDD)
- Dado `baseType=humanoid`, cuando se pide el template canónico, entonces la jerarquía de bones coincide exactamente con la documentada en el diagrama del documento de definición (root→pelvis→torso→head/brazos/piernas con sub-bones).
- Dado un `ModelIntent` con proporciones relativas (ej. "cabeza 20% más grande"), cuando `ProportionEstimator` las aplica, entonces las dimensiones resultantes respetan los rangos mín/máx del template — sin valores cero/negativos.
- Dado un valor de proporción fuera de rango, cuando se aplica, entonces se clampa al límite válido y se registra en `generationWarnings` (nunca se descarta el intent completo).
- Dado el catálogo, cuando se agrega un template nuevo (test), entonces no requiere cambios en `GeometryEngine` — prueba de extensibilidad real, no solo declarada.
- Tests unitarios sin red, sin mocks de IA (es dominio puro).

## Hecho
