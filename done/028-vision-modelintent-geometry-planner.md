# 028 — Vision→ModelIntent + Geometry planner

**Milestone:** M4 · **Depende de:** 024, 025, 005, 006 · **HUs:** HU-13, HU-14, HU-15 · **Épica:** 026

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (secciones 9.1/9.2 del master prompt). Implementar el flujo Vision→`ModelIntent` (validado contra esquema) y el Geometry planner que traduce ese `ModelIntent` a operaciones de la whitelist del Geometry Engine (005), incluyendo `AutoUv` (006) en cada operación generada.

## Criterios de aceptación (TDD)
- Dado una imagen de referencia + tipo base enviados al `VisionModelProvider` (025), cuando llega la respuesta, entonces se valida contra el JSON Schema de `ModelIntent` antes de continuar; una respuesta inválida detiene el flujo sin generar geometría.
- Dado un `ModelIntent` válido, cuando el planner genera una propuesta, entonces cada operación pertenece a la whitelist de 005.
- Dado `references/carcomido_reference.png` como entrada, cuando se genera la geometría, entonces refleja manos sobrescaladas, asimetría y silueta de ropa dañada, permaneciendo neutral/animable (HU-15).
- Dado que se completa una llamada al proveedor, cuando se persiste, entonces se guardan proveedor, modelo, versiones de prompt/esquema, IDs de referencia y la propuesta (`ai_jobs`).

## Hecho

**Decisión de alcance, sin re-preguntar (mismo patrón ya establecido por 005/006/020)**: pipeline SÍNCRONO, sin endpoint REST -- dominio puro primero, expuesto recién cuando 029 (SSE de progreso) lo necesite de verdad. `ai_jobs` se persiste solo en estado terminal (`completed`/`failed`), nunca `running` (eso es infraestructura de 029).

### Implementado
- `contracts/schemas/model-intent.schema.json` + `contracts/fixtures/model-intent-example.json` (el ejemplo literal del master prompt §9.1) -- contrato de `ModelIntent`, mismo patrón que `MobProjectModel` (004).
- `domain/model/{ModelIntent,Proportions}` (Java) + `modelvalidation/ModelIntentValidator` (mismo patrón que `MobProjectModelValidator`, 020).
- `aiorchestrator/vision/VisionAnalysisService`: imagen+baseType → `VisionModelProvider` (025) → valida contra el schema de `ModelIntent` ANTES de deserializar (AC #1).
- `aiorchestrator/planner/GeometryPlannerService`: `ModelIntent` → `StructuredReasoningProvider` (025) → `GeometryOperation[]` (whitelist de 005, aplicada gratis por la deserialización polimórfica ya existente) → `GeometryEngine.apply(model, ops, uvLayoutStrategy)` (005/006) -- la propuesta final ya es un `MobProjectModel` completo con UV recalculada, no una lista de operaciones cruda (030 la consume directo).
- `aiorchestrator/MobGenerationService`: orquesta ambas etapas + persiste `ai_jobs` (AC #4) vía nuevo `aiorchestrator/persistence/{AiJobEntity,AiJobRepository}`.
- `AlphaAutoPackStrategy` (006) ganó `@Component` -- primer consumidor Spring-managed real.
- `docs/ARQUITECTURA.md` actualizado. Sin cambios de API/Postman (sin endpoint REST en este ticket).

### Hallazgos reales, todos corregidos en este mismo ticket
1. **Transacciones**: capturar una excepción de validación, persistir la fila `failed`, y RE-LANZAR la excepción dentro del mismo `@Transactional` hace que Spring marque la transacción rollback-only por la excepción saliente -- deshaciendo la propia fila `failed` recién guardada. Corregido con `@Transactional(noRollbackFor = {InvalidModelIntentException.class, InvalidGeometryProposalException.class})`.
2. **Claude envuelve la respuesta en un bloque de código Markdown** (```` ```json ... ``` ````) con cierta frecuencia pese a que el prompt pide explícitamente "sin texto antes ni después" -- corregido despojando el fence de forma defensiva en `ClaudeMessagesClient` (el único punto por el que pasan ambas llamadas, visión y razonamiento).
3. **`claude-sonnet-5` emite un bloque `"type":"thinking"` (razonamiento extendido) sin que el request lo pida** -- con `max_tokens=4096` el modelo agotó el presupuesto completo pensando y nunca emitió texto (`stop_reason:"max_tokens"`). Subido a 16000; se agregó además un mensaje de error específico para este caso (antes era un genérico "sin bloque de texto").
4. **Un atlas de textura inicial de 64x64 no alcanza para un rig humanoide real generado por IA** (`UvAtlasOverflowException` pidió 64x70 mínimo con la primera propuesta real) -- el modelo vacío de arranque ahora usa 128x128 (mismo default "recomendado" del wizard, 027).
5. **Test-isolation**: `MockVisionProvider`/`MockReasoningProvider` son beans Spring singleton en `@SpringBootTest` -- la respuesta configurada en un test sobrevivía al siguiente si este no la reconfiguraba. Corregido con un `@BeforeEach` que resetea ambos mocks a valores válidos conocidos antes de cada test.

### Tests
22 tests nuevos (`ModelIntentValidatorTest` 5, `VisionAnalysisServiceTest` 3, `GeometryPlannerServiceTest` 4, `MobGenerationServiceTest` 5, `ClaudeMessagesClientTest` +3 de hallazgos reales). **157 tests backend, 0 fallos** -- la suite automatizada nunca llama a la API real (AC #2 del ticket 025), todo verificado contra `MockVisionProvider`/`MockReasoningProvider`.

### Verificación en vivo real (única llamada completa de esta sesión, contra `carcomido_reference.png` real -- AC #3/HU-15)
Pipeline completo (Vision real + Geometry planner real) ejecutado contra la API real de Anthropic con la imagen real del build pack. Resultado: `ai_jobs` con `status=completed`, `provider=claude`, `model=claude-sonnet-5`. Geometría generada: **6 bones** (body/head/right_arm/left_arm/right_leg/left_leg -- rig humanoide completo, neutral, animable) y **11 cuboids**, incluyendo `right_hand_claws`/`left_hand_claws` (manos claramente sobrescaladas respecto al ancho del brazo), `torso_rags`/`right_sleeve_rag`/`left_sleeve_rag` (silueta de ropa dañada) y dimensiones de brazo izquierdo/derecho ligeramente distintas (asimetría real). Las 3 características cualitativas del AC #3 se confirmaron en la salida real de la API, no en un fixture. El script de verificación fue TEMPORAL, borrado tras confirmar.

### AC verificados
- ✅ Respuesta del `VisionModelProvider` validada contra el schema de `ModelIntent` antes de continuar; una respuesta inválida detiene el flujo sin generar geometría (verificado con tests + en vivo).
- ✅ Cada operación de la propuesta del planner pertenece a la whitelist de 005 (garantizado por la deserialización polimórfica, verificado con tests de operación fuera de whitelist).
- ✅ `carcomido_reference.png` como entrada produce geometría con manos sobrescaladas, asimetría y silueta de ropa dañada, permaneciendo neutral/animable -- verificado en vivo contra la API real, no simulado.
- ✅ Al completarse una llamada, se persisten proveedor/modelo/versiones/referenceIds/propuesta en `ai_jobs` -- verificado con tests + en vivo (fila real confirmada).
