# 026 — [ÉPICA] Wizard de generación IA + SSE + Preview

**Milestone:** M4 · **Depende de:** 024, 025 · **HUs:** HU-10, HU-11, HU-12

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (mockups 02-04, Épica C). Épica que agrupa el wizard de 4 pasos, la generación de geometría por IA, el progreso vía SSE con preview no persistente, y la pantalla de Resultado con commit explícito. Se implementa en 4 subtareas (tickets 027-030).

Subtareas:
- 027 — Wizard 4 pasos UI
- 028 — Vision→ModelIntent + Geometry planner
- 029 — SSE de progreso con preview
- 030 — Pantalla Resultado: Descartar/Regenerar/Usar este modelo

## Criterios de aceptación (TDD)
- **Gate M4** (bloqueante para avanzar a M5): dado `references/carcomido_reference.png` subida vía 024, cuando se completa el wizard hasta "Usar este modelo", entonces se crea `revision_number=1` y `draft_version=1` para el mob, y el modelo generado respeta las proporciones/reglas de Minecraft (HU-15).
