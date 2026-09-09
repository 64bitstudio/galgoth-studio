# 009 — [ÉPICA] BBModel export/validation

**Milestone:** M1 (V5) y M6 (V4 completo) · **Depende de:** 004, 005, 006 · **HUs:** HU-19, HU-20, HU-21

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §7 y Addendum de implementación). Épica que agrupa el exportador `.bbmodel` y su validación — de-riskea el vertical técnico completo (`MobProjectModel → Geometry Engine → AutoUv → viewport → BBModelExporterV5 → fixture real de Blockbench → FmmCompatibilityValidator`) **antes** de tocar el pipeline de IA. Se implementa en 5 subtareas (tickets 010-014), no como un único PR gigante ni fragmentada en tickets triviales.

Subtareas:
- 010 — `BBModelExporterV5`
- 011 — Textura placeholder auto-generada + formalización del atlas
- 012 — Fixtures internas + fixture real de Blockbench (conformidad, sin importer)
- 013 — `FmmCompatibilityValidator`
- 014 — `BBModelExporterV4` (no bloquea M1; requerido antes de M6)

## Criterios de aceptación (TDD)
- **Gate M1** (bloqueante para avanzar a M2): dado un `MobProjectModel` simple (manual, sin IA), cuando se exporta con 010+011, entonces (a) genera un `.bbmodel` v5 válido, (b) la textura placeholder es válida, (c) el archivo abre en Blockbench real sin diálogo de reparación, (d) `FmmCompatibilityValidator` (013) reporta PASS.
- Dado el Gate M1 cumplido, cuando se continúa con 014 (V4), entonces esa subtarea no bloqueó ninguna de las condiciones anteriores.
- **Gate M6** (Technical Alpha final): dado el flujo E2E completo (033), cuando se valida, entonces 014 (V4) está completo — V4 es requisito del cierre del Technical Alpha aunque no lo fue de M1.

## Hecho

Las 5 subtareas (010-014) están en `done/`. **Gate M1 demostrado de punta a punta con evidencia real (2026-09-09)**: (a)/(b) confirmados desde 010/011 (tests + verificación en vivo de 028-032 contra rigs reales generados por IA); (d) confirmado repetidamente en vivo (030/032, `FmmCompatibilityValidator` reporta `Compatible` sobre un rig real de 14 cuboides). **(c) -- la única condición nunca verificada con Blockbench real hasta ahora** -- se confirmó recién al cerrar 012/014: se tomó el mismo `.bbmodel` real de 14 cuboides ya usado en las verificaciones de 030/032 (exportado por nuestro propio `BBModelExporterV5`, sin tocar nada a mano) y el PO lo abrió en su instalación real de Blockbench -- **abrió limpio, sin ningún diálogo de reparación ni advertencia**. Épica cerrada -- Gate M1 y Gate M6 (vía 012/014, ver sus propios `## Hecho`) ambos demostrados con evidencia real, no solo con tests automatizados.
