# 105 — Fixture de integración obligatorio: benchmark Carcomido end-to-end

## Objetivo
Nace de `docs/definiciones/anatomia-por-capas-generacion-mobs.md` (HU-5, HU-8). Cierra la epic de anatomía y textura por capas: corre el pipeline completo (referencia → geometría por capas → textura V2 → `.bbmodel` → validación FMM) contra el benchmark Carcomido, de punta a punta, como criterio de aceptación de toda la implementación — no como test opcional.

**Depende de:** 097, 098, 099, 100, 101, 102, 103, 104 (todos). Es el último ticket de la secuencia — valida que el conjunto funciona junto, no solo cada pieza por separado.

## Alcance
**Incluye:**
- Fixture de integración con la imagen de referencia real del Carcomido (confirmar si ya existe en el proyecto o aportarla — pregunta abierta señalada en el documento de definición).
- Test de integración que corre el pipeline completo end-to-end contra ese fixture, y adjunta el `ModelGenerationQualityReport` (ticket 104) resultante como evidencia versionada.
- Verificación explícita, no asumida, de los criterios visuales del documento original: cabeza grande cúbica, mandíbula, brazos largos, manos grandes con garras, ropa desgarrada con jirones en hombros/piernas, faldón, piel verde/gris, grietas violeta, ojos violeta — vía `featureCoverage` + inspección del modelo resultante, no solo "se ve mejor".
- Verificación de que el resultado usa HU-8 (continuidad de textura) según la estrategia que haya ganado la validación empírica del ticket 102.
- Verificación de que no hay código específico hardcodeado para "Carcomido" en ninguno de los componentes — el fixture prueba generalización, no un caso especial.

**No incluye:**
- Nueva funcionalidad — este ticket es puramente de verificación end-to-end de lo ya construido en 097-104.

## Criterios de aceptación (TDD)
- Dado el fixture Carcomido, cuando se corre el pipeline completo, entonces el test pasa en verde y el reporte de calidad queda adjunto como evidencia.
- Dado el modelo resultante, cuando se inspecciona `featureCoverage`, entonces las categorías principales del personaje (garras, ropa desgarrada, mandíbula, grietas emisivas) están representadas — se documenta explícitamente cualquiera que no lo esté, con la razón.
- Dado el modelo resultante, cuando se exporta a `.bbmodel`, entonces pasa `FmmCompatibilityValidator` sin issues críticos.
- Dado el código de los componentes 097-104, cuando se revisa, entonces no existe ninguna rama condicional específica para "Carcomido" — la mejora es generalizable.
- Suite completa del proyecto (backend + frontend) sigue en verde tras integrar todos los tickets de la epic.

## Hecho
### La pregunta abierta del documento, resuelta
La imagen de referencia **ya estaba en el repo**: `galgoth_studio_build_pack/references/carcomido_reference.png` (3MB), junto con `samples/carcomido_minecraft_cuboids.bbmodel`. No hizo falta aportarla. Verificada visualmente contra los criterios del documento original: cabeza grande cúbica, mandíbula, brazos largos, manos grandes con garras, jirones de tela en hombros y piernas, faldón, piel verde-gris, grietas violeta luminosas y ojos violeta — todos presentes en la imagen.

### Fixture del benchmark
- `contracts/fixtures/carcomido-model-intent.json`: el `ModelIntent` del personaje, **derivado de la imagen real** (no inventado), con sus 8 features y sus `featureCategories` del enum cerrado del 104 (HEAD, JAW, ARM, CLAW, TORN_CLOTH, LOINCLOTH, EMISSIVE_CRACK, EYE).
- `CarcomidoModelIntentFixtureTest`: valida el fixture contra el **mismo** `model-intent.schema.json` que cualquier respuesta real del proveedor — si no fuera un `ModelIntent` legítimo, el benchmark estaría midiendo algo que la aplicación nunca podría recibir.

### Benchmark end-to-end (`CarcomidoBenchmarkTest`, 3 tests)
Corre el pipeline completo (vision → anatomía primaria determinista → geometría secundaria acotada → UV/atlas → persistencia → export → FMM) y mide el resultado con el `ModelGenerationQualityReport` del 104.

**Evidencia real de la corrida** (impresa por el test, idéntica en dos corridas completas consecutivas):

```
[benchmark Carcomido] featureCoverage=100%, sinCobertura=[], semanticCoverage=100%, cuboids=20, fmmCompatible=100%
```

- Ninguna categoría quedó sin cobertura, así que no hay nada que documentar como faltante (el AC pedía documentar explícitamente las que no lo estuvieran).
- 20 cuboides = 14 de anatomía primaria (template humanoide real) + 6 rasgos característicos.
- El `.bbmodel` exportado pasa `FmmCompatibilityValidator` **sin ningún issue**, no solo sin críticos.

### Qué prueba este benchmark y qué NO (declarado, no dejado a interpretación)
La suite automatizada corre siempre con proveedores mock — nunca llama a la IA real. Por eso el test prueba la **mecánica** del pipeline: que las features detectadas se propagan como categorías cerradas hasta la geometría, que `semanticPart` **sobrevive todo el recorrido** (GeometryEngine → repack de UV → persistencia → round-trip JSON — justamente donde el ticket 099 encontró que se perdía en silencio), que la cobertura se mide por igualdad de enum, que los constraints no rechazan geometría secundaria bien colocada, y que el modelo exporta y valida.

**No prueba** que un modelo generado por la IA real se parezca a la imagen. Eso depende de la calidad del proveedor, no del pipeline, y solo puede verificarse con una corrida real contra `studio-dev` — la misma verificación en vivo que sigue pendiente del ticket 102 por el login. Está escrito así en el Javadoc del test para que un verde no se lea como más de lo que es.

### AC "sin código específico para Carcomido": verificado por un test, no por promesa
`NoBenchmarkSpecificCodeTest` recorre **todo** `src/main/java` y falla si el nombre del personaje aparece en el código. **Y se verificó que el guard puede fallar de verdad**: se agregó temporalmente un archivo con `return "Carcomido".equals(mobName);`, el test falló, y se borró — un guard siempre-verde no habría probado nada.

Hallazgo al escribirlo: las únicas 5 apariciones en el backend son Javadoc de **procedencia** ("estos valores salen del sample real", "hallazgo reproducido con el mob `Carcomido_v1`"). Esa trazabilidad es lo que el equipo quiere conservar y no afecta el comportamiento, así que el test excluye comentarios a propósito y lo explica en su Javadoc; lo que el AC prohíbe (un `if` sobre el nombre, un literal en runtime) sí lo detecta.

### Hallazgo real, encontrado corriendo la suite completa
El hook nuevo `setNextResponseFactory` de `MockReasoningProvider` **se filtró a `MobGenerationServiceTest`** (esperaba 15 cuboides, recibió 20: los 6 rasgos del Carcomido). Es exactamente el mismo patrón de fuga por contexto de Spring cacheado ya documentado en el 099 — reaparecido porque resetear campo por campo obliga a acordarse de cada campo nuevo en cada clase de test. **Corregido de raíz**: `MockReasoningProvider.reset()` limpia todo su estado mutable de una vez, y las clases de test lo llaman en vez de resetear campos sueltos. Agregar estado mutable al doble ahora queda cubierto en todos lados automáticamente.

### Estado final de la epic
Backend **580/580** en verde (dos corridas completas consecutivas, daemon fresco). Frontend 913/913, typecheck y lint limpios. Tickets 097-105 implementados y mergeados; queda pendiente únicamente la **verificación en vivo del ticket 102** contra `studio-dev`, bloqueada por el login (no por código).
