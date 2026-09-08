# 014 — `BBModelExporterV4` (compatibilidad)

**Milestone:** no bloquea M1 · requerido antes de M6 · **Depende de:** 010 · **HUs:** HU-19, HU-21 · **Épica:** 009

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (sección 15 del master prompt, adaptador de compatibilidad v4). Implementar el exportador `.bbmodel` versión 4 (sin la separación `groups`/`outliner` de v5) como subtarea de compatibilidad de la misma épica de export — **no bloquea el Gate M1** (que solo exige v5), pero **debe estar completo antes del Gate M6** (Technical Alpha final).

## Criterios de aceptación (TDD)
- Dado un `MobProjectModel` simple, cuando se exporta en formato v4, entonces produce un `.bbmodel` con `meta.format_version` correspondiente a v4 y estructura de outliner de esa versión.
- Dado el mismo `MobProjectModel` exportado en v4 y v5, cuando se comparan, entonces ambos representan la misma geometría/jerarquía, solo con la estructura de formato distinta.
- Dado los archivos `.bbmodel` reales v4 mínimos (ver 012), cuando se agregan como fixtures de conformidad para v4, entonces `BBModelExporterV4` pasa la comparación estructural.
- Dado el flujo E2E de aceptación (033), cuando se ejecuta contra el Gate M6, entonces V4 está disponible y probado — su ausencia bloquea el cierre del Technical Alpha aunque no haya bloqueado M1.
