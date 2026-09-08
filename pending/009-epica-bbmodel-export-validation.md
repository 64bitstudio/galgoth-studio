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
