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

## Hecho

Las 4 subtareas (027-030) están en `done/`. **Gate M4 demostrado de punta a punta contra la API real de Anthropic** (verificación en vivo del ticket 030, 2026-09-09): wizard completo con `carcomido_reference.png` real → generación completa (4 etapas SSE reales, 028/029) → avance automático a "Resultado" → "Usar este modelo" → confirmado en Postgres, en una sola transacción: `mobs.current_revision_number=1`, `mob_revisions(revision_number=1, created_by='ai')`, `mob_drafts(draft_version=1)`. Las proporciones/reglas de Minecraft (HU-15) fueron confirmadas cualitativamente en la verificación en vivo del ticket 028 (rig de 6 bones/11 cuboids con manos sobredimensionadas, ropa irregular, asimetría real de brazos) y reconfirmadas estructuralmente en 030/031/032 sobre el rig de 14 cuboides usado en sus propias verificaciones en vivo. Épica cerrada -- se mueve a `done/` recién ahora (housekeeping al cerrar 032): el archivo nunca se movió en su momento pese a que la nota de cierre de 030 ya declaraba la épica conceptualmente cerrada.
