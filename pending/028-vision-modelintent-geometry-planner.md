# 028 — Vision→ModelIntent + Geometry planner

**Milestone:** M4 · **Depende de:** 024, 025, 005, 006 · **HUs:** HU-13, HU-14, HU-15 · **Épica:** 026

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (secciones 9.1/9.2 del master prompt). Implementar el flujo Vision→`ModelIntent` (validado contra esquema) y el Geometry planner que traduce ese `ModelIntent` a operaciones de la whitelist del Geometry Engine (005), incluyendo `AutoUv` (006) en cada operación generada.

## Criterios de aceptación (TDD)
- Dado una imagen de referencia + tipo base enviados al `VisionModelProvider` (025), cuando llega la respuesta, entonces se valida contra el JSON Schema de `ModelIntent` antes de continuar; una respuesta inválida detiene el flujo sin generar geometría.
- Dado un `ModelIntent` válido, cuando el planner genera una propuesta, entonces cada operación pertenece a la whitelist de 005.
- Dado `references/carcomido_reference.png` como entrada, cuando se genera la geometría, entonces refleja manos sobrescaladas, asimetría y silueta de ropa dañada, permaneciendo neutral/animable (HU-15).
- Dado que se completa una llamada al proveedor, cuando se persiste, entonces se guardan proveedor, modelo, versiones de prompt/esquema, IDs de referencia y la propuesta (`ai_jobs`).
