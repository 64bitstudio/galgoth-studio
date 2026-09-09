# 017 — Jerarquía + selección sincronizada

**Milestone:** M2 · **Depende de:** 016 · **HUs:** HU-05 · **Épica:** 015

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (layout `hierarchy | viewport | inspector`, Visual Contract punto 7). Panel lateral con el árbol de bones/cuboides, sincronizado bidireccionalmente con la selección del viewport (016).

## Criterios de aceptación (TDD)
- Dado un `MobProjectModel` cargado, cuando se renderiza el panel de jerarquía, entonces muestra bones y sus cuboides hijos en la estructura correcta.
- Dado un clic en un nodo del árbol, cuando se selecciona, entonces el elemento correspondiente se resalta también en el viewport 3D.
- Dado un clic en un cuboid del viewport, cuando se selecciona, entonces el nodo correspondiente se resalta en el árbol de jerarquía.

## Hecho

- **`selectionStore.ts`**: primer store Pinia real del proyecto (Pinia estaba registrado desde el bootstrap sin uso hasta este ticket). `selectedCuboidId` + `select(id)` — fuente única de verdad de la selección.
- **`hierarchyTree.ts#buildHierarchyTree`**: construcción PURA del árbol bone→cuboids→sub-bones (soporta bosque con múltiples raíces, no asume un único bone raíz), separada de cualquier componente Vue para poder testearla sin montar nada (AC #1).
- **`HierarchyPanel.vue` + `HierarchyBoneNode.vue`**: árbol recursivo (un SFC se auto-referencia por su nombre de archivo, sin import explícito — patrón estándar de Vue 3 para recursión). Cada cuboid es un nodo clickeable que llama `selection.select(cuboid.id)`; el nodo del cuboid seleccionado se marca con una clase CSS.
- **Picking en el viewport**: `ThreeViewportService.pickCuboidIdAt(clientX, clientY)` hace raycasting contra los hijos DIRECTOS del grupo del mob, filtrando por `mesh.userData.cuboidId` (nunca `mesh.name`, que puede repetirse entre cuboids) — así nunca confunde un marcador de pivote de bone (sin ese `userData`) con un cuboid real, y nunca compite con el outline de selección (hijo del mesh, un nivel más profundo, fuera de la búsqueda no-recursiva).
- **Hallazgo real de UX, corregido antes de terminar (no pedido explícitamente por el AC, pero necesario para que la interacción no se sintiera rota)**: un arrastre de órbita (ticket 016) también dispara un evento DOM `click` nativo al soltar el mouse, lo que causaría una selección/deselección accidental cada vez que el usuario simplemente rota la cámara. Se agregó un guard de distancia en `ThreeViewport.vue` (`CLICK_DRAG_THRESHOLD_PX = 5`): si el mouse se movió más de 5px entre `pointerdown` y `click`, se ignora como selección (fue un drag de órbita, no un click real).
- **Decisión de alcance no pedida explícitamente, pero coherente con el AC**: clic en espacio vacío del viewport deselecciona (`pickCuboidIdAt` devuelve `null`, se propaga tal cual a `selection.select(null)`) — comportamiento estándar esperado en cualquier editor 3D, documentado aquí para que quede explícito y no se lea como un efecto secundario no intencional.
- **Verificación visual en vivo (Claude in Chrome)**: clic en el árbol resalta en el viewport (AC #2); clic en un cuboid del viewport resalta el nodo correspondiente en el árbol (AC #3, probado con `raggedShoulder_L`, un cuboid anidado 2 niveles de profundidad); clic en vacío deselecciona ambos lados; sin errores de consola.

**Tests**: 20 nuevos (`hierarchyTree.spec.ts`, 3; `selectionStore.spec.ts`, 4; `HierarchyPanel.spec.ts`, 3; `ThreeViewport.spec.ts`, 3 nuevos de picking/drag-guard + ajuste de Pinia en los 3 ya existentes) — 62 tests totales en frontend, 0 fallos. Verificado: `npx vue-tsc -b` sin errores, `npm run lint` sin errores, `npm run build` exitoso, revisión visual en vivo completa sin errores de consola.

**Fuera de alcance de este ticket:** selección de bones (solo cuboids son seleccionables este ciclo, coincide con la capacidad de outline del viewport); multi-selección (ningún AC la pide).
