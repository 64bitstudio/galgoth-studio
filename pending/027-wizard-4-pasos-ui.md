# 027 — Wizard 4 pasos UI

**Milestone:** M4 · **Depende de:** 024, 002 · **HUs:** HU-10 · **Épica:** 026

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (mockups 02-04). Construir la navegación y UI de los 4 pasos: Referencia → Configuración → Generación → Resultado, fiel al Visual Contract.

## Criterios de aceptación (TDD)
- Dado el paso "Referencia" con una imagen subida (vía 024), cuando se avanza, entonces se llega a "Configuración".
- Dado "Configuración", cuando se completan nombre/tipo base/resolución, entonces el tipo base propuesto por IA (de 028) aparece pre-seleccionado y editable manualmente.
- Dado que se confirma la configuración, cuando se avanza, entonces se dispara el job de generación (029) y se entra a "Generación".
