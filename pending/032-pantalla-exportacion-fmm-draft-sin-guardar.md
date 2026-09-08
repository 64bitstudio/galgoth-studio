# 032 — Pantalla de exportación (estado FMM + draft sin guardar)

**Milestone:** M6 · **Depende de:** 013, 020 · **HUs:** HU-19

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (mockup 11, HU-19 y Addendum de implementación). Implementar la pantalla de exportación mostrando el estado de compatibilidad FMM (013) y, cuando el draft tiene cambios sin guardar, ofreciendo explícitamente **[Guardar y exportar]** (primaria), **[Exportar última versión guardada]**, **[Cancelar]**.

## Criterios de aceptación (TDD)
- Dado que exporto un mob, cuando se genera el archivo, entonces el exportador usa `mob_revisions` (última guardada) — nunca el draft en curso.
- Dado cambios en el draft sin guardar (`draft_version` más nuevo que la última revisión), cuando se abre la pantalla de exportación, entonces se muestran las tres acciones: Guardar y exportar / Exportar última versión guardada / Cancelar.
- Dado "Guardar y exportar", cuando se confirma, entonces primero se crea la revisión (mismo mecanismo de 020) y después se exporta esa revisión recién creada.
- Dado "Exportar última versión guardada", cuando se confirma, entonces se exporta sin tocar el draft actual.
- Dado el resultado de 013, cuando se muestra en pantalla, entonces cada error de compatibilidad es específico y accionable.
