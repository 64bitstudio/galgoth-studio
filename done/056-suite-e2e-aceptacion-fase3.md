# 056 — Suite de aceptación E2E de Fase 3

**Milestone:** M10 · **Depende de:** 033, 043, 044, 048, 050, 054, 055 · **HUs:** HU-43 · **Épica:** O (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (HU-43, análoga a HU-23/ticket 033 de Fase 1+2). Automatiza el flujo E2E completo de Fase 3: partiendo de un mob con geometría ya usable (Fase 1+2), pintar/generar textura → verla en vivo en 3D → Guardar → exportar `.bbmodel` con la textura real → abre en Blockbench sin diálogos de reparación.

## Criterios de aceptación (TDD)
- Dado un mob con geometría ya usable (revisión ≥ 1, mismo punto de partida que HU-23), cuando pinto manualmente y/o genero por IA parte de su textura (`MockImageProvider`/`MockReasoningProvider`, 025/051 — sin necesidad de la API real de OpenAI/Anthropic) y hago clic en "Guardar", entonces se crea una nueva `mob_revision` cuyo `model_jsonb` refleja la textura actualizada, no el placeholder.
- Dado que exporto el mob, cuando genero el `.bbmodel`, entonces el archivo incluye la textura real — la textura placeholder checkerboard (011) queda reservada solo para mobs sin ninguna región pintada.
- Dado el `.bbmodel` exportado con textura real, cuando lo abro en Blockbench real, entonces abre sin diálogos de reparación (fixture nueva, mismo criterio que 009/012/014).
- Dado que valido el modelo, cuando corre la validación (HU-20), entonces sigue sin errores pendientes, ahora también con contenido de textura real.
- Dado que la suite corre en CI, cuando termina, entonces reporta verde — si aparece un bloqueo de infra análogo al de 033 (Docker-outside-of-Docker, CORS, etc.), se documenta explícitamente y se somete al PO la misma decisión que ya se tomó en 033 (aceptar el riesgo vs. seguir invirtiendo en el diagnóstico) — nunca se oculta un gap de CI en silencio.

## Hecho

Implementado end-to-end, incluyendo el cierre de DOS gaps reales encontrados durante este mismo ticket (ampliaciones de alcance decididas explícitamente por el PO en checkpoint, no improvisadas) que impedían que el AC #1/#2 fueran siquiera satisfacibles antes de escribir la suite. Los 5 AC del ticket están cumplidos y verificados; el AC de CI hereda la misma decisión ya tomada en 033 (ver abajo).

### Gap #1 (AC #1) — el editor manual de textura nunca persistía nada real

Al preparar la suite se confirmó que "pintar a mano + Guardar crea una `mob_revision` con textura real" NO era satisfacible: `textureEditorStore.ts`/`TextureCanvas.vue` (046/047) pintaban el atlas en memoria pero nunca lo subían al backend, ni se conectaban a `draftModelStore.ts` — solo el camino de generación por IA (054, vía "Aplicar") persistía textura real hoy. Reportado antes de escribir código; el PO decidió explícitamente cerrarlo dentro de 056:

- `frontend/src/api/textureUploadApi.ts` (nuevo): cliente de `PUT /api/mobs/{mobId}/texture` (045, backend-only hasta ahora) — mismo patrón de bytes crudos que `thumbnailApi.ts`/`referenceImagesApi.ts`.
- `frontend/src/editor/texture/textureAtlasEncode.ts` (nuevo): codifica el `TextureAtlas` en memoria a un `Blob` PNG real (canvas + `toBlob`) — dirección inversa de `pngImportDecode.ts` (048), aislado por el mismo motivo (jsdom no implementa un contexto 2D real).
- `frontend/src/editor/texture/textureFlush.ts` (nuevo): el ÚNICO módulo que conecta `textureEditorStore` con `draftModelStore` — sin acoplarlos entre sí (ninguno de los dos importa al otro; `textureEditorStore.ts` conserva intacta la garantía de independencia que su propio docstring exige desde 046). Sube el atlas vigente y devuelve el `model` con `texture.storageKey` actualizado al valor OFICIAL que el backend calculó.
- `EditorToolbar.vue`: `handleSave` hace `await flushPaintedTexture(model)` ANTES de `saveRevision` — mismo criterio "flush obligatorio antes de crear una Revision" que HU-30/HU-31 (Diseño técnico §6), ya defendido del lado backend desde 045 (`DraftPersistenceService.requireTextureAssetPersisted`). Si no hay ningún atlas cargado (tab Textura nunca abierto en la sesión) es un no-op transparente; si el flush falla, el Guardar completo se aborta igual que un `saveRevision` fallido — nunca se crea una Revision con una referencia de textura colgante.

### Gap #2 (AC #2) — el exportador nunca embebía la textura real

Investigando el pipeline de export para armar la suite, se confirmó un segundo gap, distinto y no relacionado con el primero: `BBModelExporterV5`/`V4` **nunca leían `model.texture().storageKey()`** — todo export embebía siempre el checkerboard placeholder (011), incluso con textura real ya persistida (045/046-054). Reportado explícitamente antes de tocar código (toca una zona que el PO ya defendió activamente una vez — "Hallazgo A revertido", exportador como serializador puro, 041/044); el PO aprobó cerrarlo también, con el enfoque propuesto:

- `BBModelExportSupport.buildPlaceholderTexture(w,h)` → `buildTexture(w,h,byte[] realPngBytes)`: con bytes reales los embebe tal cual (nombre `"texture"`); `null` preserva EXACTAMENTE el comportamiento de siempre (checkerboard, nombre `"placeholder"`). Mismo slot/UUID fijo para ambos casos (la POSICIÓN en `textures[]` es lo único que `Face.texture` referencia, nunca el `id`).
- `BBModelExporterV5`/`V4.export(model)` sigue existiendo, delega en un nuevo overload `export(model, byte[] realTexturePngBytes)`. El exportador SIGUE siendo un serializador 100% puro — no gana ninguna dependencia de `AssetStorageService`/infraestructura.
- `MobExportService` (que ya tiene acceso a los repos) resuelve los bytes reales vía `AssetStorageService.get(storageKey)` cuando `storageKey != null` y se los pasa al exportador — mismo patrón exacto que `LegacyUvNormalizationService` (044): resolución explícita del CALLER, nunca dentro del exportador. Si el `storageKey` ya no existe en el storage (invariante roto en teoría, defendido en profundidad desde 045), cae al placeholder con un `WARN` explícito en vez de romper el export.
- Tests nuevos: `BBModelExporterV5RealTextureTest`/`BBModelExporterV4RealTextureTest` (unitarios, equivalentes a `BBModelExporterV5PlaceholderTextureTest`: con bytes reales se embeben tal cual; sin ellos, el placeholder sigue idéntico, incluido el determinismo byte a byte) + un caso end-to-end nuevo en `MobExportControllerTest` (sube textura real vía `PUT /texture` → guarda Revision → exporta → confirma bytes reales embebidos y `fmmCompatible=true`, HU-20).

### Suite Playwright nueva (AC #1/#2/#3/#4)

`frontend/e2e/fase3-textura-acceptance.spec.ts` (mismo patrón que `technical-alpha-acceptance.spec.ts`/033): proyecto vacío → mob por IA → "Usar este modelo" (ya crea revisión ≥ 1, mismo punto de partida que HU-23) → tab Textura, un trazo real de pincel sobre el canvas → tab Modelo, Guardar (flush + nueva `mob_revision` con textura real) → Exportar → confirma `textures[0].name === "texture"` (nunca `"placeholder"`) con un PNG real decodificable (firma PNG + dimensiones) → panel FMM ("Modelo listo para usar en tu servidor", HU-20) en verde. Se ejercita el camino MANUAL a propósito (el que estaba roto y este ticket cerró); el camino IA (054/055) ya tiene cobertura propia (componente + backend).

### 3 bugs reales adicionales, encontrados corriendo la suite completa (033+056) de punta a punta de verdad

Ninguno relacionado con los dos gaps ya decididos por el PO — corregidos como mantenimiento necesario para un E2E genuinamente verde (regla del equipo: "sin parches silenciosos" — no se debilitó ningún AC ni se dejó la suite roja). Nunca detectados antes porque esta suite no corre en CI (mismo gap de 033) y nadie la había vuelto a correr localmente tras los tickets que la rompieron en silencio:

1. **`GenerationEventBroadcaster.sendTo` solo capturaba `IOException`** — una generación mock (casi instantánea) puede completar el job en OTRO hilo exactamente mientras `replay()`/`publish()` todavía iteran sobre el mismo emitter recién suscrito; `SseEmitter.send()` en ese caso lanza `IllegalStateException` (no `IOException`), que se propagaba sin capturar y tumbaba el endpoint SSE completo con un 500 — el `EventSource` del navegador quedaba "Reconectando al proceso..." para siempre. Ahora se trata igual que un cliente desconectado (log + remove, sin propagar). Test nuevo: `GenerationEventBroadcasterTest`.
2. **`WebConfig` (CORS) nunca incluía `PUT` en `allowedMethods`** — gap real desde el ticket 045 (`MobTextureController`), nunca ejercitado desde un navegador real hasta que este mismo ticket conectó el primer consumidor frontend de `PUT /texture` (el flush de "Guardar" de arriba). Agregado.
3. **`MockReasoningProvider.defaultEditResponseFor`** (rama "modelo real", 031) reescalaba el primer cuboid a escala 1.2 — combinado con el packing real de `AlphaAutoPackStrategy`/densidad de texel (042, posterior a 031), desborda el atlas por defecto con `UV_ATLAS_OVERFLOW` real (rechazo de negocio correcto, no un bug de validación). Escala reducida a 1.05 (sigue siendo un resize real y visible, dentro del headroom que el packing de hoy reserva) — explícitamente test/dev-only (`AI_REASONING_PROVIDER=mock`), cero impacto en el comportamiento real con Claude.

Además, `technical-alpha-acceptance.spec.ts` (033) tenía varios selectores desactualizados por pasadas de UX posteriores nunca re-verificadas en E2E (ticket 039: "Proyecto vacío" → "Crear nuevo proyecto", "Add cuboid" → "Agregar cuboide"; 039/050: `MobCard` ahora abre con un `<button class="mob-card__open">`, no un `<a>`; `.mob-editor__hierarchy` nunca existió, el panel real es `.hierarchy-panel`) y un paso que combinado con el bug #3 producía un `UV_ATLAS_OVERFLOW` real (reemplazado por un move de geometría, que no crece el footprint UV) — corregidos para que la suite completa corra de verdad, no solo la mitad nueva.

### CI — mismo gap conocido, misma decisión ya aceptada por el PO en 033

La suite sigue sin correr en el `Jenkinsfile` compartido (Docker-outside-of-Docker/CORS entre contenedores hermanos, ver `## Hecho` de 033 para las 6 rondas de diagnóstico) — no se reabrió el diagnóstico, se hereda la decisión ya tomada por el PO el 2026-09-09. `scripts/e2e-up.sh` gana `AI_IMAGE_PROVIDER=mock` (antes solo vision/reasoning) para que cualquier escenario que ejercite el pipeline de generación de textura por IA use `MockImageProvider`, nunca OpenAI real.

### Verificación real, no asumida

Corrida localmente contra un stack aislado equivalente al de `scripts/e2e.sh` (Postgres+MinIO+backend+frontend+Chromium reales) — en puertos alternos porque, en esta sesión, el checkout compartido tenía su propio stack corriendo en los puertos fijos de siempre (proceso externo a este ticket, no tocado). Mismas variables de entorno/comandos que `scripts/e2e-up.sh`. **2/2 specs en verde, repetido 3 veces consecutivas** (secuencial con `--workers=1` y en paralelo con `--workers` default, igual que invoca `scripts/e2e.sh`).

- **Backend completo**: 409/409 tests (incluye Testcontainers Postgres+MinIO reales).
- **Frontend completo**: 539/539 tests, `npm run lint`/`vue-tsc -b` sin hallazgos.

### Fase 3 cerrada

Este es el último ticket de Fase 3 (17/17, tickets 040-056) — editor de textura/UV manual + generación de textura por IA + persistencia/exportación de textura real, de punta a punta, con su propia suite de aceptación E2E (mismo criterio de cierre que el Technical Alpha/Fase 1+2, ticket 033).
