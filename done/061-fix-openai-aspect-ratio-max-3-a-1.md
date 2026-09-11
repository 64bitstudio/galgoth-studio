# 061 — Fix crítico: OpenAI rechaza sheets con aspect ratio mayor a 3:1

**Milestone:** post-M8 (hallazgo en producción/dev) · **Relacionado:** 051, 053, 059, 060 · **Épica:** J (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Destapado por la misma verificación en vivo del ticket 060 contra `studio-dev`: una vez redondeadas las dimensiones a múltiplo de 16 (058x8 -> 64x16), la API real de OpenAI Images rechazó la llamada con:
```
400 Bad Request: "Invalid size '64x16'. The maximum supported aspect ratio is 3:1."
```
`64x16` es ratio 4:1 -- el redondeo del ticket 060 arregló la divisibilidad pero no consideró el aspect ratio máximo real de la API.

## Criterios de aceptación (TDD)
- `OpenAiImageProvider` agranda (nunca encoge) el lado más chico del sheet, después del redondeo a múltiplo de 16, hasta que el ratio quede dentro de 3:1 -- en ambas orientaciones (ancho>alto y alto>ancho).
- Un ratio ya dentro de 3:1 (incluyendo exactamente 3:1) no se altera.
- Test de regresión explícito con las dimensiones EXACTAS del caso real que falló (64x16 -> 64x32) y con el caso end-to-end completo del reporte original (58x8 -> 64x32, combinando 059+060+061).
- Ningún otro punto del pipeline cambia -- mismo mecanismo de "margen inerte descartado por `TextureSheetSlicer`" ya usado en 060.
- Suite completa de backend en verde.

## Hecho
Implementado:
- `backend/src/main/java/.../aiorchestrator/provider/OpenAiImageProvider.java`: `sizeParam` ahora, después de redondear a múltiplo de 16, agranda el lado más chico (`ceilDiv` + redondeo a 16 otra vez) si el ratio excede `MAX_ASPECT_RATIO = 3`. Javadoc documenta el hallazgo y deja explícito que "ratio exactamente 3:1 se acepta" es una lectura razonable del mensaje de error, NO verificada en vivo -- a confirmar/ajustar si un futuro intento real dice lo contrario.
- `backend/src/test/java/.../aiorchestrator/provider/OpenAiImageProviderTest.java`: +3 tests (horizontal, vertical, límite exacto 3:1 sin alterar) + actualizado el test de 060 (58x8 ahora termina en 64x32, no 64x16, porque ambos hallazgos se combinan en el mismo caso real).

**TDD real**: confirmado que los 3 tests nuevos fallan contra el código sin este fix (`git stash`, re-corrida, 3 `AssertionError` reales) antes de aplicar la corrección.

**Tests**: backend 417/417 (+3 desde 060), sin regresiones. `./gradlew clean test` corrido localmente antes de push.

**Hallazgo real del gate de Sonar (S5976, PR #81, corregido antes de mergear)**: "Replace these 5 tests with a single Parameterized one" -- los 5 tests de ajuste de `size` (los 3 nuevos de este ticket + los 2 de 060) compartían exactamente la misma forma (llamar `generateTextureSheet` con `(width, height)` y verificar `size` esperado). Consolidados en un único `@ParameterizedTest`/`@MethodSource` (`generateTextureSheet_ajusta_el_size_a_las_restricciones_reales_de_la_API_de_OpenAI`), mismo patrón ya establecido en `ModelIntentValidatorTest`. El contexto histórico de cada caso real (059/060) se preservó como comentario en cada `Arguments.of(...)`, no se perdió información.

**Verificación en vivo pendiente**: repetir "Generar con IA" (parte `head`) contra `studio-dev` una vez mergeado y desplegado.

**Mejora continua** (tercer hallazgo consecutivo del mismo tipo -- 059/060/061): la única forma en que estos 3 bugs reales de la integración con OpenAI salieron a la luz fue probando de verdad contra la API real, iterando error por error. Ningún test unitario con valores convenientes los hubiera encontrado. Reforzado el hallazgo ya señalado en 060: para integraciones con una API externa real, al menos un ciclo de verificación en vivo (no solo mocks) antes de dar por cerrado un ticket que la toca por primera vez.
