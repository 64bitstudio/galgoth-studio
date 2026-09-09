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
- **`GET /api/projects/{projectId}/mobs`** — `MobSummary[]` (id, name, baseType, status, thumbnailKey, updatedAt) de TODOS los mobs del proyecto, ordenados por `updatedAt` descendente. El filtro por nombre del buscador (AC #3) es **client-side** sobre esta lista -- sin parámetro de búsqueda en el backend, mismo criterio de simplicidad que el dashboard de proyectos (021). `404 Not Found` (`PROJECT_NOT_FOUND`) si el proyecto no existe.
- Rename/Delete/Duplicate a nivel de mob individual **no están en el alcance de este ticket** (a diferencia de proyectos en el 021) -- el AC de 022 solo pide crear y listar.

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
- **`GET /api/projects`** — devuelve `ProjectSummary[]` (id, name, mobCount, mobThumbnails ≤3, createdAt, updatedAt), sin los soft-deleted, ordenados por `updatedAt` descendente. `mobThumbnails[].thumbnailKey` es `null` mientras no exista pipeline de thumbnails (ticket futuro) -- el frontend renderiza un placeholder genérico, nunca bloquea el listado (AC #5). El frontend calcula el indicador "+N" como `mobCount - 3` cuando `mobCount > 3` (AC #3).
- **`GET /api/projects/{id}`** — `ProjectDetail` (sin el grid completo de mobs, eso es HU-04/ticket 022); `404 Not Found` (`PROJECT_NOT_FOUND`) si no existe o está soft-deleted.
- **`PATCH /api/projects/{id}`** — body `{name}`. Mismo criterio de validación que crear.
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

## Rutas previstas (según `docs/definiciones/galgoth-studio-mvp.md`, sección 19 del master prompt)

```text
POST   /api/projects
GET    /api/projects
GET    /api/projects/{id}
PATCH  /api/projects/{id}
DELETE /api/projects/{id}

POST   /api/projects/{id}/mobs
GET    /api/mobs/{mobId}
PATCH  /api/mobs/{mobId}

POST   /api/mobs/{mobId}/references
POST   /api/mobs/{mobId}/ai/analyse
POST   /api/mobs/{mobId}/ai/generate-geometry
POST   /api/mobs/{mobId}/ai/edit-geometry

POST   /api/mobs/{mobId}/validate
POST   /api/mobs/{mobId}/export/bbmodel
GET    /api/jobs/{jobId}/events   (SSE)
```

Endpoints de "Apply"/"Usar este modelo" (mismo mecanismo de commit que Guardar, ver `docs/ARQUITECTURA.md`) se documentan aquí conforme aterricen los tickets `030`/`031`.

La colección Postman vive en `postman/galgoth-studio/` — se actualiza junto con cada endpoint nuevo (convención del equipo, ver `docs-and-task-folder-workflow`).
