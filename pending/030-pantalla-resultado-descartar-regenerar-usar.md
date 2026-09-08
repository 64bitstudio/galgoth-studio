# 030 — Pantalla Resultado: Descartar/Regenerar/Usar este modelo

**Milestone:** M4 · **Depende de:** 029, 020, 013 · **HUs:** HU-12 · **Épica:** 026

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (HU-12 y Diseño técnico §5 — "Usar este modelo" reutiliza el mecanismo de commit). Implementar la pantalla de Resultado con las tres acciones explícitas, reutilizando el mecanismo transaccional de Guardar (020) para crear la primera revisión, y mostrando el estado de compatibilidad real vía `FmmCompatibilityValidator` (013) — no un estimado.

## Criterios de aceptación (TDD)
- Dado que la generación termina exitosamente, cuando se llega a "Resultado", entonces se ve el modelo propuesto, conteo de cuboides/bones, y el estado de compatibilidad calculado corriendo 013 sobre la propuesta — el mob todavía no tiene ninguna revisión persistida.
- Dado "Resultado", cuando se ve la pantalla, entonces hay exactamente tres acciones: Descartar, Regenerar, Usar este modelo.
- Dado "Descartar", cuando se confirma, entonces la propuesta se abandona sin crear draft ni revisión.
- Dado "Regenerar", cuando se confirma, entonces se descarta la propuesta actual y se dispara un nuevo job (029).
- Dado "Usar este modelo", cuando se confirma, entonces se crean `mob_revisions.revision_number=1` y `mob_drafts.draft_version=1` en la misma transacción (mismo mecanismo que 020/031), `mobs.current_revision_number` pasa de 0 a 1, y se habilitan "Editar modelo"/"Exportar" — nunca ocurre un Apply implícito antes de este clic.
