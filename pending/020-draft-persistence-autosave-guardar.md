# 020 — Draft persistence + autosave dirty-check + Guardar

**Milestone:** M2 · **Depende de:** 019, 003 · **HUs:** HU-08, HU-09, HU-22 · **Épica:** 015

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §4 y HU-22 — contrato Command/Draft/Revision). Implementar la API/backend mínima para:
- persistir `mob_drafts` (autosave debounced),
- obtener el draft actual de un mob,
- incrementar `draft_version` **únicamente** cuando el contenido cambia materialmente (dirty flag/hash/versión local — detalle de implementación libre),
- crear una `mob_revision` mediante la acción explícita "Guardar" (snapshot completo e inmutable).

## Criterios de aceptación (TDD)
- Dado que el usuario deja de interactuar unos segundos y el contenido cambió materialmente desde el último draft persistido, cuando el autosave dispara, entonces `PATCH`/equivalente persiste `mob_drafts.draft_model_jsonb` e incrementa `draft_version` en 1.
- Dado que el autosave dispara pero el contenido es idéntico al último persistido, cuando corre, entonces no se escribe nada y `draft_version` no se incrementa.
- Dado un `GET` del draft de un mob que aún no tiene ninguno, cuando se consulta, entonces responde de forma explícita que no existe (no un draft vacío implícito) — mob nuevo: `current_revision_number=0`, sin fila en `mob_drafts`.
- Dado clic en "Guardar", cuando se confirma, entonces se valida el draft actual y se crea una nueva fila inmutable en `mob_revisions` con `revision_number` incrementado, actualizando `mobs.current_revision_number`.
- Dado que no hay cambios desde la última revisión guardada, cuando se intenta "Guardar", entonces la acción está deshabilitada o no genera una revisión duplicada.
- Dado que el draft falla la validación de invariantes al guardar, cuando ocurre, entonces no se crea la revisión y se retornan los errores específicos.
