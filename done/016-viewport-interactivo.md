# 016 — Viewport interactivo

**Milestone:** M2 · **Depende de:** 008, 002 · **HUs:** HU-05, HU-06 · **Épica:** 015

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Visual Contract, mockup 05). Extender el viewport de solo-render (008) con cámara orbital, grid de piso, gizmos de bone/pivot, outline de selección y reset de cámara.

## Criterios de aceptación (TDD)
- Dado el viewport cargado, cuando el usuario rota/pan/zoom con el mouse, entonces la cámara responde en tiempo real.
- Dado un cuboid seleccionado, cuando se renderiza, entonces tiene un outline visible que lo distingue de los no seleccionados.
- Dado un bone con pivot definido, cuando se muestra su gizmo, entonces el pivot es visualmente distinguible del bounding box del cuboid.
- Dado el botón "reset cámara", cuando se presiona, entonces la vista vuelve a la posición/ángulo inicial por defecto.

## Hecho

- **Cámara orbital**: `OrbitControls` (`three/examples/jsm/controls/OrbitControls.js`) creado una única vez en el constructor de `ThreeViewportService`, con `enableDamping`. `controls.update()` se llama en cada frame del render loop (requerido por el damping). Verificado en vivo (Claude in Chrome): arrastrar el mouse sobre el canvas orbita la cámara en tiempo real.
- **Grid de piso**: `THREE.GridHelper` agregado una vez junto con las luces (mismo motivo: no duplicarlo cada vez que una pantalla reutiliza el singleton).
- **Outline de selección**: `buildMobGroup(model, selectedCuboidId?)` agrega un `LineSegments`+`EdgesGeometry` como HIJO del mesh del cuboid seleccionado (no un objeto hermano) -- hereda automáticamente la posición/orientación ya calculada para el mesh, sin duplicar la lógica de composición de la cadena de bones. Probado con test unitario (el mesh seleccionado tiene el hijo `selection-outline`, los demás no) y verificado visualmente.
- **Gizmo de pivot de bone**: ya existía desde el ticket 008 (marcador esférico de color distinto) — cumple el AC tal cual (una esfera de color es visualmente distinguible de una caja con material plano); no se agregó nada adicional por no haber una razón real para hacerlo más complejo.
- **Reset de cámara**: `ThreeViewportService.resetCamera()` vuelve la posición de cámara y el `target` de los controles a constantes fijas, y el dev harness expone un botón que lo llama. Verificado en vivo: orbitar, luego resetear, vuelve exactamente a la vista inicial.
- **Alcance de "selección"**: este ticket NO construye la sincronización real con una jerarquía (eso es 017) — el dev harness agrega un `<select>` de solo-prueba para poder verificar visualmente el outline sin esperar a 017. `selectedCuboidId` es la superficie (prop de `ThreeViewport.vue`, parámetro de `ThreeViewportService.setModel`/`buildMobGroup`) que 017 conectará a un store compartido real.

**Tests**: 8 nuevos (`buildMobScene.spec.ts`, 2; `ThreeViewportService.spec.ts`, 3 de cámara/selección + ajustes de precisión en 2 existentes por drift de punto flotante del damping) — 49 tests totales en frontend, 0 fallos. Verificado: `npx vue-tsc -b` sin errores, `npm run lint` sin errores, `npm run build` exitoso, revisión visual en vivo completa (grid, órbita, outline, reset) sin errores de consola.
