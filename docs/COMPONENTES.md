# Componentes — Galgoth Studio

Sin pantallas productivas implementadas todavía. Este archivo se completa conforme cada pantalla aterrice, siempre contrastada contra su mockup correspondiente (ver "Visual Contract" en `docs/definiciones/galgoth-studio-mvp.md`).

## Sistema de diseño base (ticket `002-sistema-diseno-base-visual-contract`)

Implementado en `frontend/src/design-system/`:

- **Tokens** (`tokens/tokens.css`): colores, tipografía (Inter Variable + JetBrains Mono, self-hosted vía `@fontsource`), escala de espaciado, radios, sombras, anillo de foco — valores fieles al master prompt §2.
- **Componentes base** (`components/`): `GButton` (primary/secondary/danger/ghost, hit target ≥40px), `GSidebar` (Inicio/Mis proyectos/Explorar/Plantillas arriba, Configuración/Usuario abajo — corregido contra el mockup real 2026-09-08, ver Addendum de `docs/definiciones/galgoth-studio-mvp.md`; "Nuevo proyecto" es la tarjeta CTA del dashboard, no un ítem de sidebar; sin "Volver"), `GStatusPill` (estado con ícono + color, nunca solo color), `GPanel` (superficie austera, deliberadamente sin sombra por defecto para desalentar anidar cards), `GTabs` (patrón Modelo/Textura/Animación, con Textura/Animación presentes pero deshabilitadas este ciclo).
- **Íconos** (`icons/`): set propio de línea simple (sin librería externa) — home, folder, búsqueda, grilla, engranaje, avatar, fieles a `mockups/01_inicio_mis_proyectos.png`. `IconNewProject` (voxel+"+") queda sin usar por ahora, reservado para la tarjeta CTA del ticket 021.
- **Gap conocido, documentado a propósito:** "Usuario" en `GSidebar` se renderiza como un ítem de nav simple, sin el tratamiento de tarjeta de perfil (avatar + subtítulo) que muestra el mockup — depende de datos reales que no existen todavía en este ticket.
- **Vitrina de desarrollo** (`design-system/Showcase.vue`, ruta `/dev/design-system`): no es una pantalla productiva — existe para contrastar visualmente el sistema contra `mockups/00_all_views.png` antes de construir pantallas reales sobre él.

Pantallas reales (dashboard, editor, wizard, etc.) todavía no existen — llegan en los tickets 021+.

## Viewport Three.js (ticket `008-threejs-viewport-solo-render-dev-harness`)

Implementado en `frontend/src/viewport/`:

- **`ThreeViewport.vue`**: wrapper que adjunta el canvas Three.js compartido (`ThreeViewportService`, singleton) a su contenedor mientras está montado. No crea su propio `WebGLRenderer` — sienta la base para que 016 (viewport interactivo) y 023 (thumbnails) reutilicen el mismo canvas sin agotar contextos WebGL.
- **`ViewportHarness.vue`** (ruta `/dev/viewport-harness`, NO enlazada desde la navegación productiva): abre el sample real Carcomido directamente en el viewport, sin pasar por creación de proyecto/mob (021/022, que no existen todavía). Se retira o queda oculta detrás de un flag una vez M3 esté listo, igual que `/dev/design-system`.
- Renderiza los 24 cuboids del sample (más un marcador esférico por cada uno de los 6 bones, en su pivote mundial compuesto — puede quedar visualmente oculto dentro de la geometría opaca, es cosmético) usando exclusivamente el `CoordinateSystemContract` para toda transformación.
- Cámara orbital (`OrbitControls`), grid de piso, outline de selección y botón de reset de cámara (ticket 016) — ver `frontend/src/viewport/ThreeViewportService.ts`.

## Jerarquía + selección (ticket `017-jerarquia-seleccion-sincronizada`)

Implementado en `frontend/src/editor/`:

- **`HierarchyPanel.vue`** + **`HierarchyBoneNode.vue`** (recursivo, se auto-referencia por nombre de archivo): árbol bone→cuboids→sub-bones, construido por la función pura `hierarchyTree.ts#buildHierarchyTree` (testeable sin montar un componente). Cada cuboid es un nodo clickeable; los bones son solo organizativos este ciclo (no seleccionables).
- **`selectionStore.ts`**: primer store Pinia real del proyecto — `selectedCuboidId` compartido entre el árbol y el viewport (`ThreeViewport.vue`), sincronización bidireccional real (clic en cualquiera de los dos resalta en el otro).
- El dev harness (`/dev/viewport-harness`) ahora muestra el árbol de jerarquía y el viewport lado a lado como demo de la sincronización.

## Herramientas de transformación + Add/Delete/Duplicate (ticket `018-herramientas-transformacion-add-delete-duplicate`)

Implementado en `frontend/src/editor/` y `frontend/src/viewport/`:

- **`geometryOperations.ts`**: espejo TS puro del `GeometryEngine` backend (005) -- `moveCuboid`/`resizeCuboid`/`rotateCuboid`/`setBonePivot`/`setBoneRotation`/`createCuboid`/`createBone`/`removeCuboid`/`duplicateCuboid`, más `computeBoneRemovalImpact`/`removeBoneCascade` (BFS de descendientes) para la advertencia de borrado en cascada. A diferencia del backend (motor de batch atómico con resolución `tempRef`, pensado para lotes generados por IA), esta versión es un set de funciones puras aplicadas una a una desde la UI de confianza -- sin batch ni `tempRef`. Toda creación/redimensión reutiliza `layoutUv` (007) para recalcular la UV, la misma autoridad determinista que el backend.
- **`draftModelStore.ts`** (Pinia): estado en memoria del mob que se edita -- fuente de verdad única que leen `ThreeViewport.vue` y `HierarchyPanel.vue` (ya no reciben el modelo por prop). Envuelve cada operación de `geometryOperations.ts`, captura rechazos (`InvalidGeometryError`, `UvAtlasOverflowError` de 007, etc.) en `lastError` y los loguea con `console.warn` -- ningún rechazo queda invisible. Persistencia real (guardar/autosave) llega en el ticket 020; por ahora solo vive en memoria.
- **Gizmos 3D de arrastre** (`ThreeViewportService.ts` + `ThreeViewport.vue`, vía `TransformControls` de Three.js): Move/Scale/Rotate sobre el cuboid seleccionado, con el patrón "commit al soltar" -- el drag mueve el mesh libremente en cada frame, pero la mutación al `draftModelStore` (y por lo tanto la validación de geometría) solo ocurre en `mouseUp`, calculando el delta final contra una foto tomada en `mouseDown`. Esto evita que `setModel()` (que reconstruye TODOS los meshes en cada mutación) invalide la referencia que `TransformControls` está arrastrando a mitad de un drag. Si la operación resultante es rechazada (ej. escala a 0), el mesh se refresca a la última posición válida en vez de quedar visualmente desincronizado del modelo real.
  - Translate: delta mundial convertido a espacio local del cuboid (sin cadena de rotación de bones todavía -- el mundo y el local coinciden mientras el bone padre no rote).
  - Scale: multiplicativo directo desde `(1,1,1)`, igual semántica que `resizeCuboid` (escala alrededor del centro).
  - Rotate: el modo "local" de `TransformControls` con un solo anillo arrastrado siempre expone un eje unitario limpio (`rotationAxis`) y un ángulo (`rotationAngle`) -- verificado contra el código fuente real, no asumido. Se mapea directo a un delta de un solo eje. El anillo libre (rotación compuesta sin eje dominante) se ignora deliberadamente: el contrato de `rotateCuboid` solo expresa un delta de un eje por vez.
  - **Hallazgo real de tipos**: `@types/three` no declara `rotationAxis`/`rotationAngle` como propiedades de `TransformControls` (solo sus eventos `*-changed`), aunque existen en runtime vía `defineProperty` en el código fuente real -- requiere un cast de tipos documentado en el código, no es una suposición sobre el comportamiento real.
- **Pivote de bone** (`HierarchyBoneNode.vue`): edición vía 3 inputs numéricos (no gizmo 3D -- los bones no son seleccionables en el viewport, ver 017) que llaman `draft.setPivot`.
- **Borrado con advertencia de cascada** (`HierarchyBoneNode.vue`): antes de confirmar, muestra cuántos bones/cuboids hijos se eliminarían (`computeBoneRemovalImpact`) con botones explícitos Confirmar/Cancelar -- sin `confirm()` nativo del navegador.
- **`EditorToolbar.vue`**: botones Move/Scale/Rotate (controlan el modo del gizmo compartido) + Add cuboid/Add bone/Duplicate/Delete, y muestra `draft.lastError` cuando una operación es rechazada.
- **Verificado en vivo** (Claude in Chrome, `/dev/viewport-harness` contra el fixture real Carcomido, 24 cuboids/6 bones): selección + gizmos de Move/Scale/Rotate confirmados visualmente (el cuboid se mueve/escala/rota y el cambio persiste en el modelo tras soltar), Delete y Add bone confirmados, edición de pivote confirmada (el marcador esférico del bone se reubica), advertencia de cascada + Cancelar confirmados. **Hallazgo real (no un bug -- comportamiento correcto)**: el fixture Carcomido ya satura su atlas UV de 64×64 -- tanto "Add cuboid" como "Duplicate" son rechazados en vivo con `UvAtlasOverflowError` (`el atlas actual (64x64) no alcanza... se requieren al menos 64x88/64x114`), y el rechazo se refleja correctamente en `draft.lastError` sin aplicar ningún cambio parcial. Confirma que la integración con AutoUv (007) valida extremo a extremo, no solo en los tests unitarios.

## Command stack de Undo/Redo (ticket `019-command-stack-undo-redo`)

Implementado en `frontend/src/editor/draftModelStore.ts` (mismo store, no uno separado -- ver razonamiento en el comentario de cabecera del archivo):

- Un "Command" no es una clase propia: es simplemente la referencia al `MobProjectModel` inmediatamente anterior a cada edición exitosa, guardada en una pila (`undoStack`). Esto es seguro y barato porque toda función de `geometryOperations.ts` es pura -- nunca muta su modelo de entrada -- así que las referencias históricas nunca se corrompen por una mutación posterior; no hace falta clonar nada.
- `undo()`/`redo()` mueven el modelo actual entre `undoStack`/`redoStack`, reasignando `model` -- nunca crean ni destruyen una `mob_revision` (ese concepto ni siquiera existe todavía en este store, llega en el 020). Una operación RECHAZADA (geometría inválida, referencia inexistente) nunca genera un Command -- no hay nada que deshacer de un no-op.
- `load()` (cargar un mob nuevo) reinicia ambas pilas -- un mob nuevo empieza una historia de edición nueva, no hereda la del anterior.
- Un Command nuevo aplicado después de un `undo()` descarta la rama de redo pendiente (historial lineal estándar, sin árbol de ramas).
- **`EditorToolbar.vue`**: botones Undo/Redo (deshabilitados según `draft.canUndo`/`draft.canRedo`) + atajos de teclado estándar Cmd/Ctrl+Z (deshacer) y Cmd/Ctrl+Shift+Z (rehacer), ignorados mientras el foco esté en un `<input>`/`<textarea>` para no pelear con el undo nativo del propio campo (ej. editando un pivote).
- **Verificado en vivo** (Claude in Chrome, `/dev/viewport-harness`): Delete → Undo (restaura el cuboid) → Redo (lo vuelve a eliminar) confirmado tanto con los botones como con los atajos de teclado (Ctrl+Z / Ctrl+Shift+Z), sin errores de consola.

## Pantallas previstas (12, ver mockups/00_all_views.png del build pack)

1. Inicio / Mis proyectos
2. Nuevo proyecto / mob setup
3. Generación IA
4. Resultado IA (Descartar / Regenerar / Usar este modelo)
5. Editor de modelo (`hierarchy | viewport | inspector`)
6. Edición mediante IA
7. Editor de textura *(fuera de alcance del Technical Alpha)*
8. Generador IA de textura *(fuera de alcance del Technical Alpha)*
9. Animación *(fuera de alcance del Technical Alpha)*
10. Biblioteca de animaciones *(fuera de alcance del Technical Alpha)*
11. Exportación
12. Detalle de proyecto

Estructura de carpetas prevista en `frontend/src/` según `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §3).
