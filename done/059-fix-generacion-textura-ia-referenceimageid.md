# 059 — Fix crítico: la generación de textura por IA falla siempre (referenceImageId mal derivado)

**Milestone:** post-M8 (hallazgo en producción/dev) · **Relacionado:** 053, 054 · **Épica:** J (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Reportado por el PO: en `studio-dev`, cualquier "Generar con IA" de textura falla siempre con `"Fallo inesperado durante la generación de textura."` justo después de la etapa `mapeando_caras` (job real `2ea1ba73-2ebc-48c7-8f18-fb77e8c44f20`, error persistido en `ai_jobs.error`: *"El modelo no tiene ninguna imagen de referencia subida -- TextureGenerationSheetPlanner asume que el caller (HU-36/37, ticket 054) ya validó esta precondición antes de invocar el planner."*).

Root cause confirmado por auditoría de código: `TextureGenerationSheetPlanner` derivaba el `referenceImageId` de la sheet leyendo `model.referenceImages()` (campo de `MobProjectModel`) -- pero NINGÚN flujo real de la aplicación puebla ese campo jamás (`MobGenerationService.emptyModelFor` lo inicializa en `List.of()` y nunca se vuelve a tocar; solo los tests lo simulaban a mano con un `ReferenceImage` sintético). La imagen de referencia REAL de un mob vive exclusivamente en la tabla `reference_images` (`ReferenceImageRepository`), ya resuelta y validada por `TextureGenerationService.startGeneration` (`mostRecentReference`) ANTES de invocar al planner -- ese id simplemente nunca se le pasaba.

Esto hacía que la generación de textura por IA estuviera rota al 100%, para cualquier mob, en cualquier ambiente -- no es un flake ni un caso límite.

## Criterios de aceptación (TDD)
- `TextureGenerationSheetPlanner.plan(...)` recibe el `referenceImageId` como parámetro explícito (ya resuelto por el caller) -- deja de leer `model.referenceImages()`.
- Un `referenceImageId` nulo o en blanco es rechazado explícitamente (`IllegalArgumentException`) -- error del CALLER, no una inferencia silenciosa.
- `TextureGenerationService` pasa el id ya resuelto en `TextureGenerationJobContext.referenceImageId()` (la misma `ReferenceImageEntity` validada en `startGeneration`).
- Test de regresión explícito: el planner funciona correctamente con un `MobProjectModel` cuyo `referenceImages()` está VACÍO (el estado real de cualquier modelo en producción) -- este es el caso que el suite anterior NUNCA cubrió.
- Suite completa de backend en verde (412 tests hasta este punto, sin restar cobertura).

## Hecho
Implementado:
- `backend/src/main/java/.../aiorchestrator/texture/TextureGenerationSheetPlanner.java`: `plan(model, texturePlan, boneId)` → `plan(model, texturePlan, boneId, referenceImageId)`; eliminado el método privado `referenceImageId(MobProjectModel)` y el import de `ReferenceImage` (ya no depende de ese campo). Rechazo explícito de `referenceImageId` nulo/en blanco. Javadoc de clase actualizado documentando el hallazgo completo.
- `backend/src/main/java/.../aiorchestrator/texture/TextureGenerationService.java`: el único call site pasa `context.referenceImageId().toString()` -- el id YA resuelto y validado en `startGeneration()` vía `mostRecentReference(mobId)`.
- `backend/src/test/java/.../aiorchestrator/texture/TextureGenerationSheetPlannerTest.java`: `modelWith(...)` ya NO simula un `ReferenceImage` en el modelo (queda con `referenceImages()` vacío, fiel a la realidad de producción); todos los call sites de `plan(...)` pasan el id explícito; se retiró el test `unModeloSinImagenDeReferencia_lanzaExcepcionExplicita` (encodeaba el bug real como comportamiento esperado) y se agregó `unReferenceImageIdNuloOEnBlanco_lanzaExcepcionExplicita_esResponsabilidadDelCaller`.

**Tests**: backend 412/412, sin regresiones. `./gradlew clean test` corrido localmente antes de push.

**Verificación en vivo, post-merge, contra `studio-dev`** (mismo mob y el mismo job que originó el reporte -- `Carcomido_v1`, parte `head`): confirmado que el pipeline ya avanza correctamente más allá de `mapeando_caras` -- el `IllegalStateException` del planner ya no ocurre. Esta misma verificación destapó un hallazgo NUEVO y SEPARADO (no parte de este ticket, enmascarado hasta ahora por el bug de arriba): el job vuelto a correr falló en una etapa posterior (`generando_textura`) con `"OPENAI_API_KEY no está configurada"` -- ninguno de `deploy/docker-compose.{dev,qa,prod}.yml` define esa variable. Decisión explícita del PO (10 sep 2026): la agrega él mismo a los `.env` del servidor (no requiere cambio de código) -- ver addendum en `docs/ARQUITECTURA.md`.

**Mejora continua propuesta** (regla 10 de CLAUDE.md): este bug sobrevivió 054-058 y el propio ciclo de QA/Sonar porque el test unitario del planner simulaba a mano el campo que la app real nunca puebla -- un patrón de "el fixture de test miente sobre la forma real de los datos". Vale la pena, para el próximo ciclo, un lint/convención de equipo: cuando un test construye un fixture de dominio con un campo que el código de producción nunca asigna en ningún punto real (auditable con un grep como el usado acá), señalarlo como sospechoso. No se automatiza en este ticket por ser fuera de alcance de un fix puntual -- reportado para que el PO decida si amerita tooling dedicado.
