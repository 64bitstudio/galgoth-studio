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
