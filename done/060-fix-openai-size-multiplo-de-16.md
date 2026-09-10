# 060 — Fix crítico: OpenAI rechaza el tamaño de sheet si no es múltiplo de 16

**Milestone:** post-M8 (hallazgo en producción/dev) · **Relacionado:** 051, 053, 059 · **Épica:** J (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Destapado por la propia verificación en vivo del ticket 059 contra `studio-dev` (una vez resuelto el bug de `referenceImageId` y agregada `OPENAI_API_KEY`, la llamada real a OpenAI finalmente se ejecutó): la API real de OpenAI Images (`gpt-image-2.5-sunburst-2026-09-08`) rechazó la llamada real con:
```
400 Bad Request: "Invalid size '58x8'. Width and height must both be divisible by 16."
```
`OpenAiImageProvider.generateTextureSheet` mandaba `sheet.sheetWidth()/sheetHeight()` crudos (dimensión real de la cara UV más pequeña del mob de prueba) como parámetro `size` de la API, sin redondear a un múltiplo de 16 -- rompiendo la llamada real para cualquier sheet cuya dimensión no calzara por casualidad.

**Por qué ningún test lo detectó**: mismo patrón que el hallazgo de 059 -- los 6 tests existentes de `OpenAiImageProviderTest` usaban por casualidad dimensiones YA múltiplos de 16 (64x32/128x128/16x16), nunca una dimensión real "fea" como las que produce `ShelfBinPacker` sobre caras UV pequeñas.

## Criterios de aceptación (TDD)
- `OpenAiImageProvider` redondea `width`/`height` hacia ARRIBA al múltiplo de 16 más cercano antes de mandarlos como `size` a la API, tanto en `/v1/images/generations` como `/v1/images/edits`.
- Ningún otro punto del pipeline (`TextureGenerationSheetPlanner`/`ShelfBinPacker`/`CuboidFacePlacement`/`TextureSheetSlicer`) cambia -- el redondeo es 100% interno a `OpenAiImageProvider`, seguro porque `TextureSheetSlicer` ya recorta cada placement por su `sheetRect` exacto de la imagen decodificada (cualquier margen extra por el redondeo queda simplemente descartado, mismo mecanismo ya usado para "bleed").
- Test de regresión explícito con las dimensiones EXACTAS del caso real que falló (58x8 -> 64x16).
- Un tamaño ya múltiplo de 16 no se altera.
- Suite completa de backend en verde.

## Hecho
Implementado:
- `backend/src/main/java/.../aiorchestrator/provider/OpenAiImageProvider.java`: nuevo `sizeParam(width, height)`/`roundUpToMultipleOf16(int)`, usado en `generateTextureSheet` en vez de concatenar las dimensiones crudas. Javadoc documenta el hallazgo completo y por qué el redondeo es seguro (referencia a `TextureSheetSlicer`).
- `backend/src/test/java/.../aiorchestrator/provider/OpenAiImageProviderTest.java`: +2 tests -- `generateTextureSheet_con_dimensiones_no_multiplo_de_16_las_redondea_hacia_arriba_AC_hallazgo_real` (58x8 -> 64x16, dimensiones exactas del caso real) y `generateTextureSheet_con_dimensiones_ya_multiplo_de_16_no_las_altera` (32x32 sin cambios).

**TDD real**: confirmado que el primer test nuevo falla contra el código viejo (`git stash` del fix, re-corrida, `AssertionError` real) antes de aplicar el fix.

**Tests**: backend 414/414 (+2 desde 059), sin regresiones. `./gradlew clean test` corrido localmente antes de push.

**Verificación en vivo pendiente**: repetir "Generar con IA" (parte `head`) contra `studio-dev` una vez mergeado y desplegado, para confirmar que la llamada real a OpenAI complete de punta a punta (crédito de la cuenta OpenAI ya agregado por el PO).

**Mejora continua propuesta** (regla 10 de CLAUDE.md, ya señalada en 059 y reforzada acá): dos bugs reales seguidos (059 y este) sobrevivieron sus respectivos ciclos de QA/Sonar exactamente por el mismo patrón -- tests con fixtures/valores "convenientes" que no reflejan la forma real de los datos de producción (un modelo con imagen de referencia sintética; sheets con dimensiones ya múltiplas de 16). Vale la pena una convención de equipo explícita para el próximo ciclo: al testear un integrador con una API externa con restricciones documentadas (tamaños, formatos, límites), al menos UN test debe usar un valor "feo"/límite real, no solo valores redondos convenientes.
