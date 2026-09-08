# API — Galgoth Studio

Sin endpoints implementados todavía (repo recién bootstrapeado). Este archivo se completa conforme cada ticket de `pending/`/`in-process/` aterrice su endpoint real.

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

Endpoints de guardado explícito (`Guardar`, `Apply`, `Usar este modelo`) se documentan aquí con su contrato exacto conforme aterricen los tickets `020`, `030`, `031`.

La colección Postman vive en `postman/galgoth-studio/` — se actualiza junto con cada endpoint nuevo (convención del equipo, ver `docs-and-task-folder-workflow`).
