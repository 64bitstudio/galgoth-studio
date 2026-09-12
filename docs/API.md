# API — Galgoth Studio

Este archivo se completa conforme cada ticket de `pending/`/`in-process/` aterrice su endpoint real. Primeros endpoints reales: ticket `020` (draft persistence + autosave + Guardar).

## Endpoints implementados

### CRUD de mobs (ticket `022`, HU-03/HU-04)

Implementados en `backend/.../project/api/MobController.java` + `MobService` (paquete `project.mob`).

```text
POST   /api/projects/{projectId}/mobs   -- Agregar mob (HU-03)
GET    /api/projects/{projectId}/mobs   -- grid de mobs del detalle de proyecto (HU-04)
```

- **`POST /api/projects/{projectId}/mobs`** — body `{name, baseType}` (`baseType` es uno de `humanoid`/`arachnid`/`quadruped`/`flying`/`custom`, el `CHECK` de `mobs.base_type` del ticket 003). `201 Created` con el `MobSummary` -- el mob siempre arranca en `status="draft"`, `current_revision_number=0` (default de la columna) y **sin fila en `mob_drafts`** (AC #2, no existe hasta el primer autosave/Guardar, ticket 020). `400 Bad Request` (`error: "INVALID_MOB_REQUEST"`) si el nombre está vacío o `baseType` no es uno de los 5 valores válidos. `404 Not Found` (`PROJECT_NOT_FOUND`) si el proyecto no existe.
- **`GET /api/projects/{projectId}/mobs`** — `MobSummary[]` (id, name, baseType, status, thumbnailKey, updatedAt) de TODOS los mobs del proyecto, ordenados por `updatedAt` descendente. El filtro por nombre del buscador (AC #3) es **client-side** sobre esta lista -- sin parámetro de búsqueda en el backend, mismo criterio de simplicidad que el dashboard de proyectos (021). `404 Not Found` (`PROJECT_NOT_FOUND`) si el proyecto no existe. Excluye mobs soft-deleted (ticket 039).
- Duplicate a nivel de mob individual sigue sin pedirse -- no se inventa. Rename/Delete SÍ existen desde el ticket 039 (ver abajo).

### Detalle de un mob (ticket `034`) + Renombrar/Eliminar (ticket `039`)

Implementado en `backend/.../project/api/MobDetailController.java` -- ruta ya prevista desde el bootstrap del proyecto (ver "Rutas previstas" más abajo), sin `projectId` en el path (mismo criterio que `MobDraftController`/`MobThumbnailController`: el mob ya se identifica solo por su id). Ticket 039 agrega `PATCH`/`DELETE` con el mismo criterio.

```text
GET    /api/mobs/{mobId}   -- resumen de un mob (name/baseType/status/thumbnailKey), sin projectId en el path
PATCH  /api/mobs/{mobId}   -- Renombrar (ticket 039, menú ⋮ de MobCard)
DELETE /api/mobs/{mobId}   -- Eliminar, soft-delete (ticket 039)
```

- **`GET /api/mobs/{mobId}`** — `200 OK` — `MobSummary` (mismo shape que el listado de 022). `404 Not Found` (`MOB_NOT_FOUND`) si el mob no existe o está soft-deleted. Usado por `MobEditor.vue` (034) para conocer `name`/`baseType` reales cuando el mob todavía no tiene ningún draft con qué arrancar el editor.
- **`PATCH /api/mobs/{mobId}`** — body `{name}`. Mismo criterio de validación que crear (`400 Bad Request`, `error: "INVALID_MOB_REQUEST"` si el nombre está vacío). `404 Not Found` (`MOB_NOT_FOUND`) si no existe.
- **`DELETE /api/mobs/{mobId}`** — `204 No Content`. Soft-delete (`mobs.deleted_at`, `V2__mobs_soft_delete.sql`) -- mismo mecanismo EXACTO que `projects.deleted_at` (021): ningún FK hacia `mobs` (`mob_revisions`/`mob_drafts`/`reference_images`/`ai_jobs`) tiene `ON DELETE CASCADE`, un hard-delete fallaría por violación de FK en cualquier mob con historial real. `404 Not Found` (`MOB_NOT_FOUND`) si no existe.

### Mobs recientes cruzando proyectos (ticket `071`, rediseño de Inicio)

Implementado en `backend/.../project/api/MobRecentController.java`. Ruta propia `/api/mobs/recent` (segmento literal, no colisiona con el path-variable `/api/mobs/{mobId}` de `MobDetailController` -- Spring resuelve literales antes que variables). Único endpoint del CRUD de mobs que NO requiere `projectId` conocido de antemano: "Continuar trabajando" (Inicio) necesita los mobs más recientes de CUALQUIER proyecto, algo que `GET /api/projects/{projectId}/mobs` no puede resolver sin N+1 requests -- decisión explícita del Product Owner (endpoint dedicado en vez de agregación client-side).

```text
GET    /api/mobs/recent?limit=N   -- los N mobs editados más recientemente, de todos los proyectos
```

- **`GET /api/mobs/recent`** — `RecentMobSummary[]` (id, projectId, name, baseType, status, thumbnailKey, updatedAt) -- mismo shape que `MobSummary` más `projectId` (el frontend lo necesita para navegar a `/projects/{projectId}/mobs/{mobId}/edit`, ya que el mob puede ser de cualquier proyecto). `limit` es opcional (default 3, tope 20) -- un valor ausente/inválido (`<1`, no numérico) cae al default en vez de `400 Bad Request`, es un detalle de presentación (cuántas cards caben en la fila), no un parámetro de negocio. Excluye mobs soft-deleted y mobs cuyo proyecto esté soft-deleted, ordenado por `updatedAt` descendente.

### CRUD de proyectos (ticket `021`, HU-01/HU-02)

Implementados en `backend/.../project/api/ProjectController.java` + `ProjectService`.

```text
POST   /api/projects              -- crear (HU-01)
GET    /api/projects              -- dashboard "Mis proyectos" (HU-02)
GET    /api/projects/{id}         -- detalle mínimo (HU-01 AC #1)
PATCH  /api/projects/{id}         -- Rename
DELETE /api/projects/{id}         -- soft-delete (projects.deleted_at)
POST   /api/projects/{id}/duplicate -- Duplicate (copia PROFUNDA: proyecto + todos sus mobs + su historial completo de mob_revisions + su mob_drafts actual, si tiene)
```

- **`POST /api/projects`** — body `{name}`. `201 Created` con el `ProjectDetail` si el nombre es válido; `400 Bad Request` (`error: "INVALID_PROJECT_NAME"`) si está vacío/en blanco (AC #2) -- ningún proyecto se crea.
- **`GET /api/projects`** — devuelve `ProjectSummary[]` (id, name, description, mobCount, mobThumbnails ≤3, status, createdAt, updatedAt), sin los soft-deleted, ordenados por `updatedAt` descendente. `mobThumbnails[].thumbnailKey` es `null` mientras no exista pipeline de thumbnails (ticket futuro) -- el frontend renderiza un placeholder genérico, nunca bloquea el listado (AC #5). El frontend calcula el indicador "+N" como `mobCount - 3` cuando `mobCount > 3` (AC #3). `status` (ticket 072, `"active"`/`"draft"`) es DERIVADO, no una columna real de `projects` -- `"active"` si el proyecto tiene al menos un mob fuera de `draft` (`in_progress`/`ready`), `"draft"` si todos sus mobs están en draft o no tiene ninguno.
- **`GET /api/projects/{id}`** — `ProjectDetail` (id, name, description, mobCount, createdAt, updatedAt; sin el grid completo de mobs, eso es HU-04/ticket 022); `404 Not Found` (`PROJECT_NOT_FOUND`) si no existe o está soft-deleted. `description` (ticket 073) es opcional, `null` si el proyecto no tiene.
- **`PATCH /api/projects/{id}`** — body `{name, description}`. Mismo criterio de validación de `name` que crear. `description` (ticket 073) SIEMPRE explícita en el body -- el contrato espera que todo caller la reenvíe tal cual si no la está cambiando (viaja tanto en `ProjectSummary` como en `ProjectDetail` para que cualquier pantalla pueda reenviarla sin conocerla de antemano). Un body que la omita la deja en `null` -- comportamiento intencional y documentado, no una ambigüedad oculta.
- **`DELETE /api/projects/{id}`** — `204 No Content`. Soft-delete -- el proyecto deja de aparecer en cualquier consulta, tratado como "no existe" en adelante.
- **`POST /api/projects/{id}/duplicate`** — `201 Created` con el `ProjectDetail` de la copia (`name` = original + `" (copia)"`). Copia profunda real: cada mob del original se recrea con nuevo id, y se copian TODAS sus `mob_revisions` (mismo `revision_number`, mismo `model_jsonb`) más su `mob_drafts` actual si existe -- decisión explícita del Product Owner (ticket 021).
- **"Export"** del menú de acciones del dashboard (mockup 01) está deshabilitado en el frontend -- no existe ningún endpoint de exportación de proyecto expuesto todavía (decisión del Product Owner, ticket 021; el export de un MOB individual vía `BBModelExporterV5`/V4 es un servicio de dominio interno sin controlador REST, épica futura).

### Draft persistence + autosave + Guardar (ticket `020`)

Implementados en `backend/.../project/api/MobDraftController.java`. Autoridad de negocio: `backend/.../project/draft/DraftPersistenceService.java` (ver `docs/ARQUITECTURA.md` para el diseño completo del ciclo Command → Draft → Revision).

```text
GET    /api/mobs/{mobId}/draft        -- obtener el draft actual
PATCH  /api/mobs/{mobId}/draft        -- autosave (dirty-check, nunca crea una revisión)
POST   /api/mobs/{mobId}/revisions    -- Guardar (valida y crea una revisión inmutable)
```

**`GET /api/mobs/{mobId}/draft`**
- `200 OK` — `{ mobId, draftVersion, model, updatedAt }` si el mob tiene un draft persistido.
- `404 Not Found` (`error: "DRAFT_NOT_FOUND"`) — el mob existe pero todavía no tiene ningún draft (mob nuevo, sin autosave/Guardar todavía).
- `404 Not Found` (`error: "MOB_NOT_FOUND"`) — no existe ningún mob con ese id.

**`PATCH /api/mobs/{mobId}/draft`** — body `{ model: MobProjectModel }`
- `200 OK` — `{ changed, draftVersion, updatedAt }`. `changed: false` significa que el contenido enviado es idéntico al último persistido — **no se escribió nada** (ni siquiera `updated_at`); `draftVersion` solo se incrementa cuando el contenido cambia materialmente. Sin validación de invariantes contra el JSON Schema (deliberado — ver `docs/ARQUITECTURA.md`, solo Guardar/Apply/export validan).
- `404 Not Found` (`MOB_NOT_FOUND`).

**`POST /api/mobs/{mobId}/revisions`** (Guardar) — body `{ model: MobProjectModel }`
- `201 Created` — `{ created: true, revisionNumber, reason: null }`. Crea una fila inmutable en `mob_revisions` y actualiza `mobs.current_revision_number`. `mob_drafts` NO se toca (ver diseño en `docs/ARQUITECTURA.md`).
- `200 OK` — `{ created: false, revisionNumber, reason }` si el contenido enviado es idéntico a la última revisión guardada (sin duplicar).
- `400 Bad Request` (`error: "INVALID_DRAFT"`, `details: [...]`) — el modelo no pasa la validación del JSON Schema; no se crea ninguna revisión.
- `404 Not Found` (`MOB_NOT_FOUND`).

**Todos los errores** siguen la misma forma: `{ error, message, details }` (`details` es `null` salvo en `INVALID_DRAFT`).

### Apply de geometría server-side + confirmación de resize (ticket `043`, Diseño técnico §2/§15)

Implementado en `backend/.../project/api/MobGeometryController.java`. Autoridad de negocio: `MobGeometryApplyService` (paquete `project.geometry`), que ejecuta `GeometryEngine.apply(model, ops, uvLayoutStrategy, confirmPaintLoss)` (`domain/geometry`, `domain/uv`) sobre el DRAFT actual del mob y lo persiste por el MISMO mecanismo que `PATCH /draft` (`DraftPersistenceService.autosave`) -- cierra el Hallazgo B (el editor manual calculaba geometría/UV client-side sin que el backend lo revalidara) para las 3 operaciones que afectan UV.

```text
POST   /api/mobs/{mobId}/geometry/apply    -- aplica createCuboid/resizeCuboid/removeCuboid server-side
```

**`POST /api/mobs/{mobId}/geometry/apply`** — body `{ operations: GeometryOperation[], confirmPaintLoss: boolean }`
- Whitelist CERRADA: solo `createCuboid`/`resizeCuboid`/`removeCuboid` (las únicas 3 que afectan UV). `moveCuboid`/`rotateCuboid`/`setBonePivot`/`setBoneRotation`/`parentBone`/`createBone` NUNCA pasan por este endpoint -- siguen 100% client-side + autosave debounced, sin cambios.
- `200 OK` — `{ model: MobProjectModel, draftVersion }`. `model` es el modelo COMPLETO ya recalculado server-side (geometría + UV vía `UvLayoutSelector`, mismas reglas del ticket 041), listo para `draftModelStore.commitExternalModel()` en el frontend. Persiste el draft actualizado (mismo `draft_version` que `GET`/`PATCH /draft`).
- `409 Conflict` (`error: "PAINTED_REGION_RESIZE_CONFIRMATION_REQUIRED"`, `details: ["<cuboidId>:<face>", ...]`) — el resize cambiaría el footprint de al menos una cara ya `PAINTED`, y `confirmPaintLoss` no vino en `true`. No se aplica ni persiste nada. El frontend reenvía la MISMA operación con `confirmPaintLoss: true` para confirmar.
- `400 Bad Request` (`error: "UV_ATLAS_OVERFLOW"`, `details: ["currentWidth=..", "currentHeight=..", "requiredWidth=..", "requiredHeight=.."]`) — un `createCuboid` no cabe en ningún espacio libre del atlas actual (mismas reglas del ticket 041, el atlas nunca crece en silencio).
- `400 Bad Request` (`error: "INVALID_GEOMETRY_OPERATION"`, `details: [...]`) — el batch no pasa la validación del Geometry Engine (referencia no resuelta, dimensión resultante ≤ 0, etc.).
- `400 Bad Request` (`error: "UNSUPPORTED_GEOMETRY_OPERATION"`) — se envió una operación fuera de la whitelist de este endpoint.
- `404 Not Found` (`DRAFT_NOT_FOUND` / `MOB_NOT_FOUND`) — mismos códigos que `GET /draft`.

**Frontend (`ThreeViewport.vue`/`InspectorPanel.vue`, Diseño técnico §15)**: durante el arrastre del handle de resize (`pointermove`), el preview es 100% local (sin llamada de red, mismo mecanismo visual de siempre). Solo al soltar (`pointerup`, o al confirmar un input numérico) se dispara la ÚNICA llamada a este endpoint. Si responde `PAINTED_REGION_RESIZE_CONFIRMATION_REQUIRED`, se muestra `ConfirmDialog` con el detalle de las caras afectadas -- "Confirmar" reenvía la misma operación con `confirmPaintLoss: true`; "Cancelar" descarta el preview local sin llamar a `commitExternalModel` (el cuboid vuelve a su último estado confirmado).

### Asset-service: subida de imagen de referencia (ticket `024`, HU-10)

Implementados en `backend/.../project/api/MobReferenceImageController.java`. Autoridad de negocio: `ReferenceImageService` (paquete `project.reference`), sobre `AssetStorageService` (ticket 023, mismo cliente S3 genérico que el pipeline de thumbnails). **Backend-only en este ticket** — la UI real del paso "Referencia" del wizard llegó en el ticket 027 (Wizard 4 pasos), que dependía explícitamente de este; primer consumidor frontend real: `frontend/src/api/referenceImagesApi.ts`.

```text
POST   /api/mobs/{mobId}/references          -- subir una imagen de referencia
GET    /api/mobs/{mobId}/references          -- listar las imágenes de referencia de un mob
GET    /api/mobs/{mobId}/references/{id}     -- servir una imagen de referencia ya subida
```

Límites concretos (dejados abiertos a propósito por el documento de definición para resolverse en este ticket, VoBo explícito del Product Owner): solo `image/png`/`image/jpeg`, máximo 10MB por archivo.

**`POST /api/mobs/{mobId}/references`** — body: bytes crudos de la imagen, header `Content-Type: image/png` o `image/jpeg` (NO JSON, NO multipart — mismo estilo que el thumbnail del ticket 023).
- `201 Created` — `ReferenceImageSummary { id, url, width, height, contentType, createdAt }`. `width`/`height` se decodifican SIEMPRE de los bytes reales (nunca confiados del cliente); `url` es la ruta servible por esta misma API (`/api/mobs/{mobId}/references/{id}`), no la key interna de S3. Persiste un `INSERT` en `reference_images` (append-only — puede haber varias imágenes por mob, a diferencia del thumbnail que tiene una sola key fija).
- `400 Bad Request` (`error: "INVALID_REFERENCE_IMAGE"`) — content-type fuera de la whitelist, archivo por encima de 10MB, o bytes que no decodifican como una imagen válida. El content-type del header se normaliza a `tipo/subtipo` antes de compararlo contra la whitelist (ignora parámetros como `;charset=...` que algunos clientes HTTP agregan incluso a tipos binarios).
- `404 Not Found` (`MOB_NOT_FOUND`) si el mob no existe.

**`GET /api/mobs/{mobId}/references`** — `ReferenceImageSummary[]` de todas las imágenes del mob, en orden de subida (más antigua primero). `404 Not Found` (`MOB_NOT_FOUND`) si el mob no existe.

**`GET /api/mobs/{mobId}/references/{id}`** — bytes de la imagen con su `Content-Type` real (`image/png` o `image/jpeg`, según lo que se subió). `404 Not Found` sin cuerpo si la imagen no existe; `404 Not Found` (`MOB_NOT_FOUND`) si el mob no existe.

### Pipeline de thumbnails (ticket `023`)

Implementados en `backend/.../project/api/MobThumbnailController.java`. Autoridad de negocio: `ThumbnailService` (paquete `project.thumbnail`), sobre `AssetStorageService` (paquete `asset`, cliente S3 genérico contra MinIO — ver `docs/ARQUITECTURA.md`).

```text
POST   /api/mobs/{mobId}/thumbnail    -- subir el PNG generado client-side
GET    /api/mobs/{mobId}/thumbnail    -- servir el PNG actual del mob
```

**`POST /api/mobs/{mobId}/thumbnail`** — body: bytes crudos del PNG, `Content-Type: image/png` (NO JSON, NO multipart).
- `204 No Content` — sube el PNG a MinIO (key interna fija `mobs/{mobId}/thumbnail.png`, siempre sobreescrita in-place — un upload fallido nunca corrompe el thumbnail anterior, AC de "conserva el thumbnail anterior" gratis por ser un PUT S3 atómico) y actualiza `mobs.thumbnail_key` al **path servible relativo** `/api/mobs/{mobId}/thumbnail` (no la key interna de MinIO — el frontend antepone su propio `API_BASE_URL` para armar el `<img src>`, sin conocer el detalle de almacenamiento).
- `404 Not Found` (`MOB_NOT_FOUND`) si el mob no existe.

**`GET /api/mobs/{mobId}/thumbnail`**
- `200 OK` — bytes del PNG, `Content-Type: image/png`.
- `404 Not Found` (`MOB_NOT_FOUND`) si el mob no existe; `404 Not Found` sin cuerpo si el mob existe pero todavía no tiene thumbnail subido (AC #4).

El thumbnail es un asset **derivado y best-effort**: el frontend lo genera/sube DESPUÉS de un Guardar exitoso (`EditorToolbar.vue`, botón "Guardar" real añadido en este mismo ticket — el ticket 020 solo implementó el backend de "Guardar"), en un paso separado cuyo fallo nunca revierte ni bloquea la revisión ya guardada (solo `console.warn` en el frontend).

### Persistencia content-addressed de textura (ticket `045`, HU-30/HU-31)

Implementados en `backend/.../project/api/MobTextureController.java`. Autoridad de negocio: `TextureService` (paquete `project.texture`), sobre `AssetStorageService` (mismo cliente S3 genérico que thumbnails/referencias). Ver `docs/definiciones/galgoth-studio-fase3-textura.md`, Diseño técnico §4/§6 — **el backend es la única autoridad del hash/`storageKey`**, nunca confía en uno propuesto por el cliente.

```text
PUT    /api/mobs/{mobId}/texture    -- subir el bitmap de textura, content-addressed
```

**`PUT /api/mobs/{mobId}/texture`** — body: bytes crudos de un PNG, `Content-Type: image/png` (NO JSON, NO multipart — mismo estilo que el thumbnail del ticket 023). El contrato no tiene ningún campo para que el cliente proponga un `storageKey`.
- `200 OK` — `{ storageKey: "textures/{sha256-hex}.png" }`. El backend decodifica los bytes recibidos (nunca confía en el `Content-Type` declarado), los re-codifica a PNG canónico, calcula el SHA-256 sobre esos bytes canónicos (no sobre los bytes crudos subidos — dos encoders/clientes distintos pueden producir bytes PNG distintos para el mismo contenido de píxeles; hashear la forma canónica es lo que garantiza el dedup automático de HU-31 sin importar el origen), y sube a MinIO bajo `textures/{sha256-hex}.png` — **idempotente**: si la key ya existe, no vuelve a escribirla.
- `400 Bad Request` (`error: "INVALID_TEXTURE"`) — los bytes no decodifican como una imagen válida.
- `404 Not Found` (`MOB_NOT_FOUND`) si el mob no existe.

**Flush obligatorio antes de crear una Revision** (Diseño técnico §6): el frontend debe esperar (await) la respuesta de este endpoint y usar EXACTAMENTE el `storageKey` devuelto antes de invocar "Guardar" (`POST /revisions`) o "Usar este modelo" (`POST /jobs/{jobId}/apply`) — nunca dispararlos en paralelo con un `PUT /texture` todavía en vuelo. Como defensa en profundidad (contra un cliente que por bug no respete ese orden), ambos endpoints verifican que el `storageKey` referenciado por `model.texture()` exista realmente en MinIO antes de escribir la fila: `400 Bad Request` (`error: "DANGLING_TEXTURE_REFERENCE"`) si no existe — nunca se persiste una `mob_revision` con una referencia colgante.

El dirty-check de autosave (`PATCH /draft`, ticket 020) no requiere ningún cambio: `TextureDocument.storageKey` es un `String` más dentro de `MobProjectModel`, así que la comparación de igualdad estructural ya existente lo cubre automáticamente (O(1), sin comparar bitmaps) — el `storageKey` comparado es siempre el que este endpoint devolvió, nunca uno calculado solo por el cliente.

### Pipeline de generación IA + progreso SSE + Resultado (tickets `028`/`029`/`030`, HU-11/HU-12/HU-13/HU-14/HU-15)

Implementados en `backend/.../aiorchestrator/api/GenerationJobController.java`. Orquestador: `MobGenerationService` (`aiorchestrator`), que corre el pipeline Vision→`ModelIntent`→Geometry planner→`MobProjectModel` de forma **asíncrona** (`generationExecutor`, ver `GenerationExecutorConfig`) — el nombre real difiere de `/ai/generate-geometry` previsto originalmente en la sección "Rutas previstas" de más abajo, unificado en un solo endpoint de arranque en vez de separar vision/geometría en dos llamadas HTTP distintas. `GenerationResultService` (mismo paquete `aiorchestrator`) es el lado "leer resultado"/"aceptar propuesta" (030) -- nunca re-ejecuta el pipeline de IA.

```text
POST   /api/mobs/{mobId}/generate      -- arranca un job de generación (202, no bloqueante)
GET    /api/jobs/{jobId}/events        -- progreso en vivo (SSE), reanudable con Last-Event-ID
POST   /api/jobs/{jobId}/cancel        -- cancela un job en curso (efectivo en el próximo punto de control, no instantáneo)
GET    /api/jobs/{jobId}/result        -- resumen de un job completado (conteos + compatibilidad FMM real)
POST   /api/jobs/{jobId}/apply         -- "Usar este modelo": crea la primera revisión+draft del mob
```

**`POST /api/mobs/{mobId}/generate`**
- `202 Accepted` — `{"jobId": "<uuid>"}`. Crea la fila `ai_jobs` (`status='running'`) de inmediato y devuelve el `jobId` sin esperar a ninguna llamada de IA — el pipeline real corre en otro hilo.
- `404 Not Found` (`MOB_NOT_FOUND`) si el mob no existe.
- `400 Bad Request` (`NO_REFERENCE_IMAGE`) si el mob no tiene ninguna imagen de referencia subida (024) todavía.

**`GET /api/jobs/{jobId}/events`** (`Content-Type: text/event-stream`)
- Un evento nombrado `progress` por cada avance real, `data` = `{"seq","stage","message","progressPct","payload"}`. `stage` es una de `analizando_referencia`/`detectando_silueta`/`creando_rig`/`generando_cuboides`/`preparando_resultado`/`validando_geometria`/`completado`/`fallido`/`cancelado` (las 2 del medio, ticket 038 -- ver abajo). `payload` es `null`, o `{"type":"preview_operations", addedOrUpdatedBones, addedOrUpdatedCuboids, removedCuboidIds}` (el mecanismo preferido, un delta resuelto -- nunca las `GeometryOperation` crudas con `tempId`s del proveedor) o `{"type":"preview_snapshot", model}` (solo resincronización -- se emite una única vez, en el evento `completado`).
- El **preview nunca modifica `mob_drafts` ni crea `mob_revisions`** (AC #2) -- es enteramente descartable, vive solo en el log de eventos y en memoria del cliente.
- Reconexión: el navegador reenvía `Last-Event-ID` automáticamente (`EventSource` nativo) -- el servidor reproduce el backlog persistido desde ese `seq` antes de continuar en vivo. Sin `Last-Event-ID` (conexión nueva/resumida tras un refresh de página), reproduce TODO el backlog desde `seq=1` -- el frontend (`GenerationStep.vue`) usa esto a propósito para recuperar una generación en curso sin duplicar el job (ticket 038).
- `404 Not Found` si el `jobId` no existe.

**Ticket 038 (bugfix del progreso IA) -- streaming real del Geometry Planner + switch operativo**:
- Hallazgo real (`ai_job_events` de jobs reales en `dev`, 2026-09-09): la llamada al Geometry Planner (razonamiento) tardaba 70-90s reales SIN emitir ningún evento, dejando la UI "pegada" -- causa del bug reportado, no un problema de entrega SSE/frontend.
- Con `AI_GEOMETRY_STREAMING_ENABLED=true` (default): el backend consume la respuesta de Claude incrementalmente (`stream: true` de la API de mensajes de Anthropic) y emite un evento `preview_operations` real por cada `GeometryOperation` a medida que el modelo la termina de generar -- nunca un replay post-hoc.
- Con `AI_GEOMETRY_STREAMING_ENABLED=false` (switch operativo, vía redeploy, sin cambio de código): cae al modo heartbeat -- la misma llamada bloqueante de siempre, con un ping periódico honesto (mismo `stage`/`progressPct` ya emitido, `detectando_silueta`/25%, solo el `message` cambia con el tiempo real transcurrido) mientras espera.
- `preparando_resultado`/`validando_geometria` son 2 etapas reales nuevas insertadas en el pipeline ANTES de `completado`: envuelven la aplicación final de UV (`GeometryPlannerService.applyOperations`) y la validación FMM (`FmmCompatibilityValidator`, informativa -- un modelo con hallazgos FMM sigue llegando a `completado` igual que antes, `GET /result` sigue siendo la fuente de verdad de `fmmCompatible`/`fmmIssues`).

**`POST /api/jobs/{jobId}/cancel`**
- `202 Accepted` — señala la cancelación; el job se detiene en el próximo punto de control (nunca interrumpe una llamada HTTP a un proveedor de IA ya en vuelo).
- `404 Not Found` (`JOB_NOT_FOUND`) si el `jobId` no existe.
- `409 Conflict` (`INVALID_JOB_STATE`) si el job ya alcanzó un estado terminal.

**`GET /api/jobs/{jobId}/result`** (ticket 030, HU-12, AC #1)
- `200 OK` — `{jobId, mobId, mobName, cuboidCount, boneCount, textureWidth, textureHeight, fmmCompatible, fmmIssues}`. Calculado en vivo desde `ai_jobs.proposal_jsonb` (nunca re-ejecuta `GeometryEngine`) -- `fmmCompatible`/`fmmIssues` son el resultado REAL de exportar la propuesta a `.bbmodel` (010/011) y correrle `FmmCompatibilityValidator` (013) encima, no un estimado.
- `404 Not Found` (`JOB_NOT_FOUND`) si el `jobId` no existe.
- `409 Conflict` (`JOB_NOT_COMPLETED`) si el job todavía no terminó, falló o se canceló.

**`POST /api/jobs/{jobId}/apply`** ("Usar este modelo", ticket 030, HU-12, AC #4)
- `201 Created` — `{revisionNumber, draftVersion}`. Crea `mob_revisions` (`created_by='ai'`) Y `mob_drafts` en la MISMA transacción (`DraftPersistenceService.applyGenerationProposal`, mismo mecanismo que "Guardar", 020) y avanza `mobs.current_revision_number` -- nunca ocurre implícitamente antes de este clic.
- `400 Bad Request` (`INVALID_DRAFT`) si la propuesta no pasa la validación de invariantes (defensa en profundidad -- en la práctica, una propuesta que ya pasó por `GeometryEngine` en 028 siempre es válida).
- `404 Not Found` (`JOB_NOT_FOUND`) si el `jobId` no existe.
- `409 Conflict` (`JOB_NOT_COMPLETED`) si el job todavía no terminó, falló o se canceló.

**Gap conocido en su momento, ya cerrado (ticket `034`)**: tras "Usar este modelo", el frontend navega de vuelta a `/projects/:projectId` -- desde ahí, cada tarjeta de mob ya enlaza a `/projects/:projectId/mobs/:mobId/edit` (ver "Detalle de un mob" arriba), la ruta real del editor manual (016-018) montada sobre un `mobId` real.

### Edición IA conversacional sobre un mob existente (ticket `031`, HU-17/HU-18)

Implementado en `backend/.../aiorchestrator/edit/api/AiEditController.java`, orquestado por `AiEditService` (paquete `aiorchestrator.edit`). A diferencia del pipeline de generación (028/029), es **síncrono** -- una sola llamada rápida al `StructuredReasoningProvider`, sin SSE ni job en estado `running`: cada `POST .../ai/edit-geometry` persiste un `ai_jobs` fila `job_type='edit'` ya en estado terminal (`completed`/`failed`).

```text
POST   /api/mobs/{mobId}/ai/edit-geometry   -- genera un plan de edición sobre el draft actual (200, sin tocar mob_drafts/mob_revisions)
POST   /api/jobs/{jobId}/apply-edit         -- aplica un plan ya generado (201, crea revisión+draft)
```

**`POST /api/mobs/{mobId}/ai/edit-geometry`** -- body `{"instruction": "..."}`
- `200 OK` — `{jobId, summary, beforeCuboidCount, beforeBoneCount, afterCuboidCount, afterBoneCount, changedElements, beforeModel, afterModel}`. `changedElements` (`{type, id, name, changeKind}`, `changeKind` en `added`/`modified`/`removed`) reutiliza el mismo diff puro de 029 (`GenerationPreviewDiff`) contra el draft ANTES vs. la propuesta DESPUÉS.
- `400 Bad Request` (`NO_BASE_REVISION`) si el mob todavía no tiene ninguna revisión guardada (`mobs.current_revision_number=0`) -- no existe nada que editar todavía; falla antes de gastar una llamada real a la IA.
- `404 Not Found` (`MOB_NOT_FOUND` / `DRAFT_NOT_FOUND`) si el mob no existe, o tiene revisión pero ningún draft (caso posible si "Guardar" se llamó sin autosave previo).
- `400 Bad Request` (`INVALID_EDIT_PROPOSAL`) si la IA devuelve una propuesta inválida (JSON malformado, operación fuera de la whitelist, geometría inválida) -- se persiste un `ai_jobs` fila `status='failed'` para auditoría, pero **el draft/revisión real del mob nunca cambian**.

**`POST /api/jobs/{jobId}/apply-edit`**
- `201 Created` — `{revisionNumber, draftVersion}`. Mismo mecanismo que `POST /api/jobs/{jobId}/apply` (030) -- crea `mob_revisions` (`created_by='ai'`) y `mob_drafts` en la misma transacción.
- `404 Not Found` (`JOB_NOT_FOUND`) si el `jobId` no existe.
- `409 Conflict` (`JOB_NOT_COMPLETED`) si el job no es un `edit` completado.
- `409 Conflict` (`STALE_EDIT_BASE`) si el draft/revisión base avanzaron desde que se generó el plan (otro autosave/"Guardar" ocurrió mientras tanto) -- no aplica nada; el frontend ofrece regenerar el plan contra el estado actual.

### Pipeline de generación de textura por IA + diff + Apply atómico (ticket `054`, HU-36 a HU-39)

Implementado en `backend/.../aiorchestrator/api/TextureGenerationController.java`, orquestado por `TextureGenerationService` (paquete `aiorchestrator.texture`) -- mismo patrón de orquestación que `MobGenerationService` (028/029): síncrono hasta crear la fila `ai_jobs` en `running`, el pipeline real corre en `generationExecutor`. Reutiliza `GET /api/jobs/{jobId}/events` (SSE, 029) TAL CUAL -- ese endpoint es genérico por `jobId`, sin lógica de `job_type`.

```text
POST   /api/mobs/{mobId}/ai/generate-texture   -- arranca un job de generación/regeneración de textura (202, no bloqueante)
GET    /api/jobs/{jobId}/events                -- progreso en vivo (SSE, 029, reutilizado sin cambios)
GET    /api/jobs/{jobId}/texture-result        -- diff Antes/Después de una propuesta ya completada
POST   /api/jobs/{jobId}/apply-texture         -- Apply atómico (bitmap + draft + revisión + current_revision_number)
```

**`POST /api/mobs/{mobId}/ai/generate-texture`** -- body `{"style": "faithful"|"minecraft_vanilla"|"pixel_art"|"realistic", "detailLevel": "low"|"medium"|"high", "boneId": "<id>"|null}`
- `202 Accepted` -- `{"jobId": "<uuid>"}`. Reutiliza SIEMPRE la imagen de referencia más reciente ya subida en Fase 2 -- nunca pide una nueva (HU-36 AC #1). `boneId` ausente/`null` -> `job_type=generate_texture` (HU-36, todos los bones con geometría); `boneId` presente -> `job_type=edit_texture` (HU-37, regenera solo ese bone -- una sola llamada de imagen coherente para todo el bone, nunca una por cuboid).
- `404 Not Found` (`MOB_NOT_FOUND`).
- `400 Bad Request` (`NO_BASE_REVISION`) si `mobs.current_revision_number=0` -- necesita geometría ya usable.
- `400 Bad Request` (`NO_REFERENCE_IMAGE`) si el mob no tiene ninguna imagen de referencia subida.
- `400 Bad Request` (`INVALID_TEXTURE_GENERATION_REQUEST`) -- `style`/`detailLevel` fuera de los valores aceptados, o el mob/bone objetivo no tiene ningún cuboid que texturizar.
- `400 Bad Request` (`TEXTURE_TARGET_BONE_NOT_FOUND`) -- el `boneId` pedido no existe en el modelo actual.

**Progreso SSE (Diseño técnico §13)**: nuevos valores de `stage` sobre el mismo mecanismo de siempre -- `analizando_paleta` (análisis de material/paleta vía `TexturePlanService`, 052), `mapeando_caras` (uno por bone objetivo, `TextureGenerationSheetPlanner`, 053), `generando_bone_<id>` (uno por bone/sub-sheet -- valor DINÁMICO, incluye el id real del bone; si hubo fallback/batching, el `message` agrega `"(parte N/M)"`), `componiendo_atlas` (payload `preview_texture_patch`), `limpiando_pixeles`. Esquema formal de `preview_texture_patch` (payload del evento): `{ type: "preview_texture_patch", rect: {x,y,width,height}, encoding: "base64"|"asset_url", data|url }` -- hasta 32 KB de payload base64 codificado van inline (`encoding=base64`); por encima, se sube como asset temporal a MinIO bajo `texture-previews/{jobId}/{seq}.png` (servido por `GET /api/texture-previews/{jobId}/{fileName}`, prefijo DISTINTO de `textures/`) y se emite `encoding=asset_url` con esa `url`. **Estos previews NUNCA se persisten como textura definitiva** -- ni el `rect`+`data` inline ni el asset temporal tocan `textures/{sha256}.png` ni ninguna fila de `mob_drafts`/`mob_revisions`; lo único que persiste algo real es un Apply exitoso.

**`GET /api/jobs/{jobId}/texture-result`** (HU-38)
- `200 OK` -- `{jobId, mobId, wholeModel, touchedBoneIds, touchedFaces, hasHandPaintedOverwrite, beforeAtlasPngBase64, afterAtlasPngBase64}`. `touchedFaces[]` (`{cuboidId, face, rect, handPaintedOverwrite}`) es el diff a nivel de cara; `handPaintedOverwrite=true` señala EXPLÍCITAMENTE que esa cara tenía contenido pintado a mano (o de origen desconocido/legacy) que esta propuesta sobrescribiría -- distinto de simplemente "ya tenía contenido generado por IA" (HU-37 AC #2). Nunca re-ejecuta el pipeline.
- `404 Not Found` (`JOB_NOT_FOUND`).
- `409 Conflict` (`JOB_NOT_COMPLETED`) si el job no es un job de textura (`generate_texture`/`edit_texture`) en estado `completed`.

**`POST /api/jobs/{jobId}/apply-texture`** (HU-38 AC #3, Diseño técnico §10/§16)
- `201 Created` -- `{revisionNumber, draftVersion}`. Chequeo de conflicto PRIMERO: si `mobs.current_revision_number`/`mob_drafts.draft_version` avanzaron (geometría O textura, ambas viven en el mismo `MobProjectModel`) desde que se generó la propuesta, `409 Conflict` (`STALE_TEXTURE_BASE`) -- se descarta sin tocar nada (ni MinIO ni Postgres). Sin conflicto, **atómico**: (1) sube el bitmap compuesto a MinIO bajo su `storageKey` content-addressed REAL (mismo mecanismo de `TextureService`/`PUT /texture`, 045 -- el backend recién calcula este hash acá, nunca antes) PRIMERO, fuera de la transacción; (2) UNA transacción Postgres (`DraftPersistenceService.applyGenerationProposal`, reutilizada tal cual de 030) que actualiza `mob_drafts`, inserta `mob_revisions` (texture+geometría juntas) y avanza `mobs.current_revision_number` -- todo o nada.
- **"Reject" no es un endpoint** -- simplemente no se llama a este; la propuesta queda en `ai_jobs` para auditoría, sin tocar `mob_drafts`/`mob_revisions`/MinIO.
- `404 Not Found` (`JOB_NOT_FOUND`).
- `409 Conflict` (`JOB_NOT_COMPLETED`).

**`GET /api/texture-previews/{jobId}/{fileName}`** -- sirve exclusivamente los assets temporales de `preview_texture_patch` cuando cruzan el umbral de 32 KB (ver arriba). `404 Not Found` si no existe (ya expiró o nunca existió) -- sin garantía de retención a largo plazo.

**Migración `V3__ai_jobs_texture_job_types.sql`**: `ai_jobs.job_type` acepta `generate_texture`/`edit_texture` además de `generate`/`edit`; nueva columna `target_bone_id` (nullable -- poblada solo en `edit_texture`). **Hallazgo real, señalado explícitamente**: a diferencia de lo que el ticket 054 pedía literalmente, también fue necesario actualizar `chk_ai_jobs_base_values_by_type` (003) para que `generate_texture`/`edit_texture` exijan `base_revision_number`/`base_draft_version` `NOT NULL` (mismo criterio que `edit`, nunca como `generate`) -- ambos job types SIEMPRE corren sobre un mob con geometría ya usable, necesaria para el chequeo de conflicto 409.

**Extensión aditiva de `ImageGenerationProvider` (051)**: gana `provider()`/`model()` (además de `generateImage`/`generateTextureSheet`, sin cambios) -- `TextureGenerationService` los necesita para persistir en `ai_jobs.provider`/`ai_jobs.model` el proveedor/modelo REAL usado en el paso de generación de imagen (HU-39), ya que `generateTextureSheet` devuelve solo `byte[]` (a diferencia de `VisionModelProvider`/`StructuredReasoningProvider`, que devuelven un `AiProviderResponse` completo).

**`UvRegion` gana `paintedBy` (`UvPaintOrigin`: `hand`/`ai`), aditivo** -- necesario para que HU-37 AC #2 pueda distinguir "contenido pintado a mano" de "contenido generado por IA" al construir el diff (antes de este ticket, `PAINTED` no llevaba esa información). `null`/ausente (todo JSON legacy, y toda región pintada a mano por el editor manual del ticket 047, que todavía no escribe este campo explícitamente) se trata como "origen desconocido, tratar como posible pintado a mano" -- nunca se asume `ai` por default. Serializado con `@JsonInclude(NON_NULL)` para no romper comparaciones JSON estrictas ya congeladas.

### Exportación (ticket `032`, HU-19, mockup 11)

Implementado en `MobExportController.java` (paquete `project.export`). "Guardar y exportar" NO es un endpoint compuesto: el frontend orquesta `POST /api/mobs/{mobId}/revisions` (020, "Guardar") seguido de `GET .../export/bbmodel` -- dos llamadas sucesivas reutilizando el mecanismo de Guardar tal cual, en vez de un endpoint propio que lo duplique. Difiere de la ruta "prevista" originalmente (`POST .../export/bbmodel`): es `GET`, no `POST` -- exportar es una operación de solo lectura sobre `mob_revisions`, nunca escribe nada por sí misma.

```text
GET    /api/mobs/{mobId}/export/status    -- estado de la pantalla (200, nunca falla salvo mob inexistente)
GET    /api/mobs/{mobId}/export/bbmodel   -- descarga el .bbmodel de la última revisión guardada
```

**`GET /api/mobs/{mobId}/export/status`**
- `200 OK` — `{mobId, mobName, hasSavedRevision, hasUnsavedChanges, fmmCompatible, fmmIssues}`. `fmmCompatible`/`fmmIssues` se calculan SIEMPRE contra la última revisión GUARDADA (nunca el draft en curso, decisión confirmada explícitamente con el PO) -- son `null`/`[]` cuando `hasSavedRevision=false` (nada guardado todavía). `hasUnsavedChanges=true` cuando el draft difiere de la última revisión (comparación por igualdad de valor de `MobProjectModel`, mismo mecanismo que el dirty-check de autosave, 020) o cuando hay un draft pero ninguna revisión todavía.
- `404 Not Found` (`MOB_NOT_FOUND`) si el mob no existe.

**`GET /api/mobs/{mobId}/export/bbmodel`**
- `200 OK` — el `.bbmodel` (JSON) de la última revisión guardada, como descarga (`Content-Type: application/octet-stream`, `Content-Disposition: attachment; filename="<nombre-sanitizado>.bbmodel"`). NUNCA lee `mob_drafts`.
- `404 Not Found` (`MOB_NOT_FOUND`) si el mob no existe.
- `404 Not Found` (`NO_SAVED_REVISION`) si el mob nunca tuvo ninguna revisión guardada (`current_revision_number=0`) -- nada que exportar todavía.

## Rutas previstas (según `docs/definiciones/galgoth-studio-mvp.md`, sección 19 del master prompt)

```text
POST   /api/projects
GET    /api/projects
GET    /api/projects/{id}
PATCH  /api/projects/{id}
DELETE /api/projects/{id}

POST   /api/projects/{id}/mobs
PATCH  /api/mobs/{mobId}

POST   /api/mobs/{mobId}/references

POST   /api/mobs/{mobId}/validate
```

La colección Postman vive en `postman/galgoth-studio/` — se actualiza junto con cada endpoint nuevo (convención del equipo, ver `docs-and-task-folder-workflow`).
