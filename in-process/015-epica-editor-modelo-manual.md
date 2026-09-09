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

## Estado (2026-09-08)

Las 5 subtareas (016-020) están en `done/`. **El Gate M2 tal como está escrito arriba (flujo real en el harness, contra un mob persistido) NO está demostrado todavía** -- decisión explícita del Product Owner al cerrar el ticket 020 (ver su `## Hecho`): el harness no puede tener un `mobId` real sin CRUD de mobs (ticket 021, que abre el milestone M3), así que se optó por probar el backend a fondo con Testcontainers en vez de inventar un seed solo para este Gate. Esta épica queda intencionalmente en `in-process/` -- no se mueve a `done/` hasta que el ticket 021 permita ejecutar el escenario literal del Gate M2 (harness real → mob persistido → Guardar → revisión → export sin errores) y se verifique de punta a punta.
