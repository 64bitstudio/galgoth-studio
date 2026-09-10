# 049 — Selección cruzada cuboid↔UV: face picking determinista

**Milestone:** M8 · **Depende de:** 017, 047 · **HUs:** HU-25, HU-26 · **Épica:** J (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §14, corregido explícitamente por el PO — reescritura completa). Seleccionar una cara de un cuboid en el viewport 3D resalta su región UV en el editor 2D, y viceversa. El face picking es DETERMINISTA: cada cara/grupo de material de la geometría Three.js se etiqueta con `FaceName` en su construcción — la normal del triángulo queda solo como validación/fallback, nunca como mecanismo primario.

## Criterios de aceptación (TDD)
- Dado `buildCuboidMesh` (`buildMobScene.ts`), cuando construye cada `BoxGeometry`, entonces etiqueta `mesh.userData.faceNamesByGroup: FaceName[6]` resuelto desde `CoordinateSystemContract` — sin cambios a `buildMobGroup` ni a cómo se compone la escena.
- Dado `pickCuboidFaceAt(clientX, clientY)`, cuando raycastea sobre un cuboid (incluyendo uno rotado, no solo en su orientación por defecto), entonces resuelve `{ cuboidId, face }` usando `intersection.face.materialIndex` para indexar `faceNamesByGroup` — determinista, sin calcular nada a partir de la normal.
- Dado el mismo caso, cuando se valida en modo desarrollo, entonces la normal del triángulo intersectado se usa SOLO como assert/validación (confirma que coincide aproximadamente con la normal esperada del `FaceName` resuelto) — nunca decide el resultado devuelto (test que fuerza una discrepancia deliberada de normal y confirma que el resultado de `pickCuboidFaceAt` no cambia).
- Dado que hago clic en una cara del viewport 3D, cuando lo hago, entonces la región UV correspondiente se resalta/enfoca en el editor 2D (047).
- Dado que selecciono una región UV en el editor 2D, cuando lo hago, entonces la cara correspondiente se resalta en el preview 3D.
- Dado el store nuevo `textureSelectionStore.ts` (sibling de `selectionStore.ts`), cuando se implementa, entonces NO modifica el contrato de `selectionStore.ts` — los tests existentes de selección de cuboid completo (017/031/036) siguen en verde sin cambios.

## Hecho
