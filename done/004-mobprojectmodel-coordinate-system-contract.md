# 004 — MobProjectModel: dominio + CoordinateSystemContract

**Milestone:** M0 · **Depende de:** 001 (avanza en paralelo a 003 — DB y dominio son independientes) · **HUs:** —

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (sección 7, `MobProjectModel`). Definir los tipos TS + DTOs Java + JSON Schemas del contrato compartido en `contracts/`, y formalizar el **`CoordinateSystemContract`** canónico que todo el resto del sistema (Geometry Engine, AutoUv, viewport Three.js, exportador) debe respetar sin ambigüedad.

El `CoordinateSystemContract` debe fijar explícitamente:
- unidades (Minecraft pixels, según `samples/model_spec_example.json`)
- ejes X/Y/Z y su orientación
- semántica de `from`/`to` (esquinas del cuboid)
- `origin`/pivot (punto de rotación, distinto del bounding box)
- grados vs. radianes en cada capa (dominio, Three.js, `.bbmodel`)
- orden de aplicación de rotación (rotation order)
- cómo se compone la transformación padre-hijo (bone → bone hijo → cuboid)
- tabla de mapeo explícita Three.js ↔ `MobProjectModel` ↔ `.bbmodel` (qué convierte a qué, y dónde)

## Criterios de aceptación (TDD)
- Dado `samples/model_spec_example.json`, cuando se valida contra el JSON Schema de `MobProjectModel`, entonces pasa sin errores.
- Dado un tipo TS y su DTO Java equivalente, cuando se serializa/deserializa el mismo objeto en ambos lados, entonces produce JSON estructuralmente idéntico.
- Dado el `CoordinateSystemContract` documentado (ADR en `docs/adr/`), cuando otro ticket (Geometry Engine, AutoUv, viewport, exportador) necesita convertir una coordenada/rotación entre capas, entonces usa exclusivamente las funciones de conversión de este contrato — ningún otro módulo reimplementa su propia conversión de ejes/grados/orden de rotación.
- Dado un caso de prueba con rotación de bone padre + cuboid hijo, cuando se aplica la composición de transformaciones descrita en el contrato, entonces el resultado coincide con el comportamiento esperado de Blockbench/Minecraft (verificado contra `samples/carcomido_minecraft_cuboids.bbmodel`).
- Dado los campos `texture`, `uv`, `animations`, `exportSettings` de `MobProjectModel`, cuando se inspecciona el tipo, entonces existen en el esquema aunque no tengan UI/lógica funcional este ciclo (Fase 3/4).

## Hecho

Completado 2026-09-08. PR [`#5`](https://github.com/64bitstudio/galgoth-studio/pull/5) (rama `feature/004-mobprojectmodel-coordinate-system`), CI de Jenkins en verde (build #3, tras dos correcciones reales -- ver abajo).

**Implementado:**
- `contracts/schemas/mob-project-model.schema.json` -- JSON Schema draft 2020-12, fuente de verdad formal de bones/cuboides/texture/uv/animations/exportSettings/referenceImages.
- `contracts/fixtures/model-spec-example.json` -- fixture completo (extiende `samples/model_spec_example.json`, que **no se modificó** por ser intencionalmente parcial/ilustrativo — ver README_START_HERE.md del build pack). Incluye una animación real (`samples/animation_spec_example.json`, adaptada) y una `referenceImage` de ejemplo, no arrays vacíos.
- `contracts/fixtures/coordinate-system-fixture.json` -- 3 casos compartidos TS/Java, dos analíticamente verificables a mano y uno grounded en valores reales de `samples/carcomido_minecraft_cuboids.bbmodel` (pivote/rotación/punto de `handRight`).
- `docs/adr/0001-coordinate-system-contract.md`: unidades (Minecraft pixels), ejes (convención Minecraft/Blockbench), `from`/`to`, `origin`/pivot, rotación (grados, Euler extrínseco XYZ, `R=Rz·Ry·Rx`), composición padre-hijo, mapeo Three.js↔MobProjectModel↔`.bbmodel`. La convención de orden de rotación se verificó contra una especificación comunitaria real del formato (no se asumió) — queda marcada como riesgo de bajo impacto a confirmar cuando el exportador real (ticket 010+) lo valide contra Blockbench de verdad.
- Tipos TS (`frontend/src/domain/MobProjectModel.ts`, `coordinateSystem.ts`) + DTOs Java (`backend/.../domain/model/*.java`, `CoordinateSystem.java`), con `Vec3JacksonModule`/`Vec4JacksonModule` para que el lado Java serialice arrays `[x,y,z]`/`[u0,v0,u1,v1]` igual que TS y el schema — registrados como beans de Spring, no solo usados en tests.

**Verificado (36 tests en verde: 19 TS + 17 Java):**
- Validación de schema contra el fixture (TS con `ajv`, Java con `networknt/json-schema-validator`), incluyendo un caso negativo (campo requerido faltante rechazado).
- Round-trip DTO↔JSON estructuralmente idéntico en ambos lados (TS: `JSON.stringify`+deep-equal; Java: `JSONAssert` en modo numéricamente laxo, ya que Jackson representa "0" como `0.0` y el JSON en sí no distingue ambos).
- `CoordinateSystemContract`: caso analítico a mano (rotar 90° en Z) + caso de composición padre-hijo cruzado contra una vía de cálculo independiente (rotación combinada de ángulo), no solo paridad TS/Java — mismo fixture compartido en ambos lados.

**Dos hallazgos reales del Quality Gate de Sonar, corregidos antes de cerrar (no en el plan original, sin debilitar el gate):**
1. Primer build: `new_violations=0` pero coverage del código nuevo insuficiente — varios tipos (`AnimationSpec`, `AnimationTrack`, `Keyframe`, `AnimationEvent`, `ReferenceImage`) nunca se instanciaban porque el fixture tenía `animations: []`/`referenceImages: []` vacíos. Corregido completando el fixture con datos reales (no con tests triviales solo para subir el número) — cobertura ~72% → ~94%.
2. Segundo build: `new_violations=2` (2 bugs reales, severidad media, regla `java:S2384`). Sin credenciales de API de SonarQube disponibles (`SONARQUBE_CLI_TOKEN_VM` sigue vacío) — se consultó la base de datos de SonarQube directamente, de forma read-only, vía SSH (mismo patrón ya documentado en `platform/docs/ARQUITECTURA.md`), para identificar la condición exacta del gate y las 2 issues concretas: `Face.uv` y `UvRegion.rect` eran `double[]`, que hereda `equals`/`hashCode` por referencia (no por contenido) en un record — el mismo problema que `Vec3` ya resolvía para posición/rotación. Corregido agregando `Vec4` (mismo patrón: record de 4 primitivos + `Vec4JacksonModule`), no ignorando el hallazgo ni sobreescribiendo `equals` a mano de forma ad-hoc.

**Gap conocido, fuera de alcance de este ticket (no silencioso):** 3 issues de Sonar pre-existentes de `done/003-...` (sugerencias de AssertJ `hasToString`, un lambda con múltiple throw) siguen abiertas — no bloquean este gate porque no son "nuevas" en este análisis. Quedan como limpieza pendiente, no de este ticket.

**AC #3 ("otro módulo usa exclusivamente estas funciones"):** el ADR y la implementación existen y son la única fuente correcta; la adherencia real de otros módulos (Geometry Engine, AutoUv, viewport, exportador) se verifica cuando esos tickets (005+) aterricen — no es automáticamente comprobable todavía porque esos módulos no existen aún.
