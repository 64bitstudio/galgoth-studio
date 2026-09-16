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
- Nuevo paquete `backend/src/main/java/com/galgothstudio/backend/domain/template/` (10 archivos, todos nuevos, ningún archivo existente modificado): `ProportionRange`, `TemplateBoneSpec`, `TemplateCuboidSpec`, `ProportionAdjustmentResult`, `ProportionApplier` (interfaz funcional — extensibilidad por template sin tocar código compartido), `CanonicalTemplate`, `CanonicalTemplateCatalog`, `HumanoidCanonicalTemplate`, `MinimalGenericTemplate`, `ProportionEstimator`.
- `HumanoidCanonicalTemplate`: jerarquía de 15 bones (`body` raíz → `torso` → `head`; `torso` → brazo izq/der con antebrazo+mano; `body` → pierna izq/der con espinilla+pie) coincidiendo exactamente con el diagrama del documento de definición, y 14 cuboides de anatomía primaria en rest-pose (minecraft_pixels, ADR 0001), cada uno con `semanticPart` propio (`TORSO`/`HEAD`/`ARM`/`FOREARM`/`HAND`/`LEG`/`SHIN`/`FOOT` — como string por ahora, el enum cerrado `SemanticPartCategory` llega en el ticket 104).
- `ProportionEstimator.adjust`: clampa `headScale`/`armLength`/`handScale`/`shoulderWidth` a los rangos del template (`[0.6,1.8]`/`[0.7,1.6]`/`[0.7,2.2]`/`[0.8,1.6]`) y delega la aplicación real a `ProportionApplier` del template — nunca llama IA, nunca descarta el intent, un valor fuera de rango genera un warning explícito por campo.
- `HumanoidCanonicalTemplate.applyProportions`: cabeza escala alrededor del pivote del cuello (el cuello no se mueve); cadena de brazo (hombro→codo→muñeca) recalculada para quedar contigua sin huecos en cualquier `armLength` del rango válido; mano escala alrededor de la muñeca; ancho de hombros escala simétricamente ambos lados. Torso y piernas quedan sin cambios (`ModelIntent.Proportions` no expone campo propio para ellos hoy — documentado explícitamente en el Javadoc de la clase, no es un olvido).
- `MinimalGenericTemplate` + `CanonicalTemplateCatalog.forBaseType`: cualquier `baseType` sin template completo (`arachnid`/`quadruped`/`flying`/`custom`) recibe root+body sin ajuste de proporciones, tal como acordado en el VoBo del documento de definición para esta iteración.
- **13 tests nuevos** — `CanonicalTemplateCatalogTest` (5): jerarquía completa verificada campo a campo, cada `parentId`/`boneId` referencia algo existente, fallback a template mínimo, y una prueba de extensibilidad real (construye un `CanonicalTemplate` ad hoc fuera del catálogo y lo corre a través de `ProportionEstimator` sin tocar `CanonicalTemplateCatalog` ni `GeometryEngine`). `ProportionEstimatorTest` (8): clamping con warning (incluido el caso de 4 proporciones fuera de rango a la vez → 4 warnings), escalado de cabeza/mano/hombros, contigüidad de la cadena de brazo verificada en 5 valores distintos de `armLength` a lo largo de todo el rango válido, y equivalencia exacta de torso/piernas entre proporciones neutrales y extremas.
- Suite completa del backend corrida de verdad: **500/500 en verde, 0 failures, 0 errors** — los tests preexistentes no se tocaron (confirma que el paquete nuevo no rompió nada).
- Sin UI ni endpoints tocados en este ticket (dominio puro, consumido recién por el ticket 098) — no aplica revisión visual en vivo ni actualización de Postman.
- **Hallazgo real corregido antes de commitear (no estaba en el ticket original):** el catálogo cacheaba lazily el template mínimo genérico vía `EnumMap.computeIfAbsent` sobre un mapa mutable compartido entre requests concurrentes del backend — riesgo real de condición de carrera bajo carga (`EnumMap` no es thread-safe para escritura concurrente). Corregido: el catálogo queda de solo lectura tras el arranque; `MinimalGenericTemplate.build` se reconstruye por llamada (inmutable, barato) en vez de cachearse.
