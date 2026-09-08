# 016 — Viewport interactivo

**Milestone:** M2 · **Depende de:** 008, 002 · **HUs:** HU-05, HU-06 · **Épica:** 015

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Visual Contract, mockup 05). Extender el viewport de solo-render (008) con cámara orbital, grid de piso, gizmos de bone/pivot, outline de selección y reset de cámara.

## Criterios de aceptación (TDD)
- Dado el viewport cargado, cuando el usuario rota/pan/zoom con el mouse, entonces la cámara responde en tiempo real.
- Dado un cuboid seleccionado, cuando se renderiza, entonces tiene un outline visible que lo distingue de los no seleccionados.
- Dado un bone con pivot definido, cuando se muestra su gizmo, entonces el pivot es visualmente distinguible del bounding box del cuboid.
- Dado el botón "reset cámara", cuando se presiona, entonces la vista vuelve a la posición/ángulo inicial por defecto.
