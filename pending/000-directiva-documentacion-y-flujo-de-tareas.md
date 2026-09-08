# 000 — Directiva: documentación y flujo de tareas

## Objetivo
Este proyecto sigue la convención estándar del equipo (`/pending /in-process /done /docs`, + `/postman` por ser backend) — ver memoria `docs-and-task-folder-workflow`. Este ticket documenta esa convención y sirve de referencia de cómo se ve un ticket bien formado en este repo. No hay trabajo de implementación asociado.

## Criterios de aceptación (TDD)
- Dado un ticket nuevo, cuando se crea, entonces nace en `pending/` con formato `NNN-slug-descriptivo.md`, numerado secuencialmente considerando `pending/`, `in-process/` y `done/` juntos.
- Dado que un ticket arranca su implementación real, cuando se mueve, entonces pasa a `in-process/` (nunca directo a `done/`).
- Dado que un ticket se cierra, cuando se completa (skill `cerrar-ticket`), entonces se llena su sección `## Hecho` con lo realmente implementado y se mueve a `done/`.
- Dado los 5 documentos vivos en `docs/` (`README.md`, `ARQUITECTURA.md`, `BASE_DE_DATOS.md`, `API.md`, `COMPONENTES.md`), cuando se completa un ticket que los afecta, entonces se actualizan como parte de ese mismo ticket (skill `cerrar-ticket` lo verifica).
- Dado un cambio grande (proyecto nuevo, epic mayor, arquitectura), cuando se planea, entonces pasa primero por el skill `definicion-alcance` — ya ocurrió para este proyecto: ver `docs/definiciones/galgoth-studio-mvp.md` (documento aprobado, baseline congelado).

## Hecho
