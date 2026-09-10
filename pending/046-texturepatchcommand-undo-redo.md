# 046 — TexturePatchCommand — Undo/Redo de textura por patches

**Milestone:** M8 · **Depende de:** 019, 040 · **HUs:** HU-27, HU-32 · **Épica:** K (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §9). El Undo/Redo de textura NO puede snapshotear el bitmap completo por cada trazo (inviable en memoria). Se implementa `TexturePatchCommand { rect, beforePixels, afterPixels }` acotado al rectángulo mínimo tocado, sobre una pila (`textureEditorStore.ts`) 100% independiente de la de geometría (018/019) — Ctrl+Z en el tab Textura nunca cruza al tab Modelo.

## Criterios de aceptación (TDD)
- Dado un trazo de pincel completo (`pointerdown` → varios `pointermove` → `pointerup`), cuando se registra en el historial, entonces genera EXACTAMENTE UN `TexturePatchCommand` (bounding box acumulado del trazo completo) — nunca uno por cada evento `pointermove` intermedio.
- Dado un uso de la Cubeta sobre una región contigua, cuando se aplica, entonces genera un `TexturePatchCommand` cuyo `rect` es el bounding box de la región rellenada.
- Dado un `TexturePatchCommand` con un `rect` pequeño sobre un atlas grande, cuando se mide el tamaño de `beforePixels`/`afterPixels`, entonces es proporcional al área del `rect`, nunca al área total del atlas (test explícito de tamaño, para blindar contra una regresión a snapshot completo).
- Dado un Undo sobre el último `TexturePatchCommand`, cuando se ejecuta, entonces aplica `beforePixels` sobre `rect` — operación O(área del rect). Dado un Redo posterior, entonces aplica `afterPixels`.
- Dado la pila de Undo/Redo de textura (`textureEditorStore.ts`) y la de geometría (`draftModelStore`, 019), cuando se hace Ctrl+Z estando en el tab Textura, entonces solo afecta la pila de textura — un cambio de geometría pendiente en la otra pila permanece intacto (test cruzado explícito).

## Hecho
