# 033 — Suite Playwright del flujo E2E de aceptación

**Milestone:** M6 · **Depende de:** 020, 030, 031, 032, 014 · **HUs:** HU-23

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (HU-23, criterios de aceptación núm. 1-12 del master prompt). Automatizar el flujo E2E completo del Technical Alpha: crear proyecto Galgoth → agregar mob Carcomido → generar geometría por IA → Usar este modelo → editar a mano y Guardar → editar por IA con diff y Apply → validar → exportar `.bbmodel` (v5, y v4 vía 014) → abrir en Blockbench sin diálogos de reparación.

## Criterios de aceptación (TDD)
- Dado el flujo completo descrito arriba, cuando corre la suite Playwright, entonces cada paso pasa contra un backend real (Docker Compose) con `MockProvider` (025) para las llamadas de IA.
- Dado el `.bbmodel` resultante (v5 y v4), cuando se valida contra las fixtures de Blockbench real (012), entonces ambos abren sin diálogo de reparación.
- Dado que la suite corre en CI, cuando termina, entonces reporta verde antes de considerar cerrado el Technical Alpha (Gate M6) — incluyendo que 014 (V4) esté completo, como exige el Gate M6 de la épica 009.
