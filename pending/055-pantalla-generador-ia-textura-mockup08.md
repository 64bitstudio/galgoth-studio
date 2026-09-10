# 055 — Pantalla del generador IA de textura (mockup 08)

**Milestone:** M9 · **Depende de:** 054 · **HUs:** HU-42 · **Épica:** N (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (HU-42). Pantalla "Generar con IA" desde el tab Textura, ensamblando el pipeline de 054 según el layout del mockup 08.

## Criterios de aceptación (TDD)
- Dado que abro "Generar con IA" desde el tab Textura, cuando la pantalla carga, entonces veo imagen de referencia + resultado/preview, selector de Estilo con las 4 opciones, control de Detalle (bajo-alto), y selector "Parte a generar" con granularidad de bone completo — siguiendo el mockup 08.
- Dado que el progreso del pipeline (054) se emite por SSE, cuando se muestra en esta pantalla, entonces refleja las etapas y el preview incremental (`preview_texture_patch`) en tiempo real.
- Dado el resultado final, cuando se presenta, entonces muestra el diff Antes/Después con Apply/Reject, respetando el Visual Contract vigente.
- **Revisión visual en vivo obligatoria antes de cerrar** (skill `cerrar-ticket`): contrastar contra el mockup 08 con Claude in Chrome.

## Hecho
