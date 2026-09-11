# 062 — Fix crítico: OpenAI rechaza sheets por debajo del "pixel budget" mínimo

**Milestone:** post-M8 (hallazgo en producción/dev) · **Relacionado:** 051, 053, 059, 060, 061 · **Épica:** J (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Cuarto hallazgo consecutivo de la misma cadena de verificación en vivo contra `studio-dev` (059 destapó 060, 060 destapó 061, 061 destapa este): con el ratio ya corregido (`58x8` -> `64x32`), la API real de OpenAI Images rechazó la MISMA llamada con:
```
400 Bad Request: "Invalid size '64x32'. Requested resolution is below the current minimum pixel budget."
```
Ni la documentación pública de OpenAI ni una búsqueda en vivo (`WebSearch`/`WebFetch` contra `developers.openai.com`) dieron un número exacto para ese mínimo -- a diferencia de los 3 hallazgos anteriores, donde la propia API sí incluyó el valor exacto en el mensaje de error.

## Criterios de aceptación (TDD)
- `OpenAiImageProvider` aplica un piso mínimo de 256px por lado (`MIN_SIDE_PX`), documentado explícitamente como una estimación conservadora NO verificada en vivo contra el mínimo real -- a ajustar si un futuro intento real todavía lo rechaza.
- El piso se aplica ANTES del clamp de aspect ratio (una cara que sube al piso puede volver a exceder el ratio 3:1 si el otro lado ya es grande -- ej. `1536x8` -> piso sube el alto a 256 -> eso excede 1536>256*3, dispara un SEGUNDO ajuste de ratio -> `1536x512`).
- Dimensiones ya por encima del piso no se alteran.
- Test de regresión explícito con las dimensiones EXACTAS del caso real que falló (`64x32` -> `256x256`).
- Suite completa de backend en verde.

## Hecho
Implementado:
- `backend/src/main/java/.../aiorchestrator/provider/OpenAiImageProvider.java`: nueva constante `MIN_SIDE_PX = 256`; `sizeParam` aplica `Math.max(roundUpToMultipleOf16(x), MIN_SIDE_PX)` a cada lado ANTES del clamp de aspect ratio existente (061). Javadoc documenta honestamente que el valor 256 es una estimación conservadora, no un número confirmado en vivo por la API (a diferencia de los hallazgos de 059/060, donde el mensaje de error sí daba el número exacto).
- `backend/src/test/java/.../aiorchestrator/provider/OpenAiImageProviderTest.java`: los 5 casos existentes del `@ParameterizedTest` (todos con dimensiones <256) ahora esperan `256x256` (el piso domina); +3 casos nuevos: el caso real exacto (`64x32` -> `256x256`), el caso límite de reordenamiento piso-antes-que-ratio (`1536x8` -> `1536x512`), y un caso de no-alteración cuando ambos lados ya superan el piso (`512x512` sin cambios). El primer test de la clase (`generateTextureSheet_sin_imagen_de_referencia_...`), que usaba `(64,32)` esperando `"64x32"` -- el mismo patrón de dimensión conveniente que originó 059/060 -- se corrigió a `(512,256)` para no acoplar un test de wiring genérico al valor exacto del piso mínimo.

**TDD real**: confirmado que 6 casos fallan contra el código sin el piso mínimo (`git stash` del fix, re-corrida, 6 `AssertionError` reales) antes de aplicar la corrección.

**Tests**: backend 420/420 (+3 desde 061), sin regresiones. `./gradlew clean test` corrido localmente antes de push.

**Verificación en vivo pendiente**: repetir "Generar con IA" (parte `head`) contra `studio-dev` una vez mergeado y desplegado.

**Mejora continua** (cuarto hallazgo consecutivo del mismo tipo -- 059/060/061/062): esta cadena de 4 tickets seguidos, todos encontrados exclusivamente por probar de verdad contra la API real de OpenAI, confirma que ninguna cantidad de tests unitarios con mocks hubiera encontrado ninguno de los 4 -- el diseño técnico original (`OpenAiImageProvider`, ticket 051) ya advertía explícitamente "a verificar contra la documentación real cuando 054 conecte este proveedor de punta a punta", y eso es exactamente lo que terminó pasando, varios tickets después. Reforzado una vez más: para integraciones con una API externa real, un ciclo de verificación en vivo temprano (no al final, no solo cuando el PO lo reporta como bug) hubiera encontrado estos 4 hallazgos de una sola vez en vez de 4 rondas de PR separadas.
