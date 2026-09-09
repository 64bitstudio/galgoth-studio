# API — Galgoth Studio

Este archivo se completa conforme cada ticket de `pending/`/`in-process/` aterrice su endpoint real. Primeros endpoints reales: ticket `020` (draft persistence + autosave + Guardar).

## Endpoints implementados

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
