# 015 — [ÉPICA] Editor de modelo manual

**Milestone:** M2 · **Depende de:** 008, 002 · **HUs:** HU-05 a HU-09, HU-22

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (mockup 05, Épica B). Épica que agrupa el editor de modelo manual completo — viewport interactivo, jerarquía, herramientas de transformación, Undo/Redo y persistencia de draft/revisión. Se implementa en 5 subtareas (tickets 016-020).

Subtareas:
- 016 — Viewport interactivo
- 017 — Jerarquía + selección sincronizada
- 018 — Herramientas de transformación + Add/Delete/Duplicate
- 019 — Command stack Undo/Redo
- 020 — Draft persistence + autosave dirty-check + Guardar

## Criterios de aceptación (TDD)
- **Gate M2** (bloqueante para avanzar a M4/M5): dado el sample Carcomido cargado vía el development harness (008), cuando se le agranda una mano a mano en el editor y se presiona "Guardar", entonces se crea una revisión, y el modelo exporta correctamente vía la épica 009 (M1) sin errores de validación.

## Hecho

Las 5 subtareas (016-020) están en `done/`. El Gate M2, escrito arriba, quedó intencionalmente sin demostrar al cerrar 020 (ver nota "Estado (2026-09-08)" original de este archivo, preservada en el historial de git) -- el harness no tenía un `mobId` real sin CRUD de mobs (021).

**Gate M2 demostrado de punta a punta, con evidencia real (ticket 034, 2026-09-09)**: el ticket 021 (CRUD de mobs) y el ticket 034 (ruta real del editor manual sobre un mob existente) juntos habilitaron exactamente el escenario literal del Gate -- un mob real y persistido (creado vía 021/022), abierto en el editor real (`MobEditor.vue`, 034, los mismos componentes de 016-018 sin cambios), "Guardar" real (`EditorToolbar.vue`, 020/023) → confirmado en Postgres: `mob_revisions(revision_number=1, created_by='user')`. La exportación sin errores de validación (parte final del Gate) quedó cubierta después por el ticket 032 (verificado en vivo con `curl` contra un `.bbmodel` real y válido). Épica cerrada -- se mueve a `done/` recién ahora, como housekeeping al cerrar 032, porque ninguno de los tickets intermedios (021-032) volvió a revisar explícitamente esta condición pendiente.
