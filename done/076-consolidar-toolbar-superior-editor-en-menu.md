# 076 — Consolidar la fila superior del editor de mob en un menú "⋮"

## Objetivo
La fila superior del editor de mob (`MobEditor.vue`, tabs Modelo/Textura) tenía 4 botones -- Reset cámara, Asistente IA/Generar con IA, Exportar, Guardar -- que desbordaban en viewports angostos (hallazgo real del PO, con captura). Pedido explícito: consolidar todo en un menú "⋮", dejando SOLO el botón de IA (Asistente IA/Generar con IA) visible fuera del menú.

## Alcance

### Incluye
- `MobEditor.vue`: Reset cámara/Exportar/Guardar pasan a ser 3 ítems de un `GMenu.vue` ("⋮") -- mismo componente que ya usan `ProjectCard.vue`/`MobCard.vue`/`ProjectDetail.vue`, sin lógica de dropdown nueva. El botón de IA (`GButton`) queda como el único control siempre visible fuera del menú, sin cambios en su comportamiento.
- "Guardar" mantiene su comportamiento real como ítem del menú: label dinámico ("Guardando…" mientras `activeSaveState === 'saving'`) y deshabilitado cuando `!activeCanSave` (vía `disabled` de `GMenuItem`).
- El indicador real de "Guardado"/"Cambios sin guardar" (`TextureSaveStatus.vue`, barra de estado inferior de cada tab) NO se toca -- sigue siempre visible sin pasar por el menú, así que ocultar el botón de Guardar detrás de "⋮" no oculta el ESTADO de si hay cambios sin guardar.

### No incluye
- Ningún cambio a la lógica real de reset de cámara/exportar/guardar -- se reubicaron los 3 controles, ninguno cambió de comportamiento.
- Ningún cambio a `EditorHeader.vue` (breadcrumb + tabs) ni al resto de `MobEditor.vue`.

## Criterios de aceptación (TDD)
- El botón de IA (Asistente IA/Generar con IA) sigue siendo un botón real y visible, con el mismo comportamiento exacto de antes, en ambas tabs.
- El menú "⋮" expone exactamente 3 ítems -- Reset cámara, Exportar, Guardar -- en ambas tabs, con el mismo comportamiento real que tenían como botones sueltos (Reset cámara resetea la cámara del viewport compartido, Exportar navega a la pantalla de exportación, Guardar delega en el hijo activo -- `EditorToolbar`/`TextureCanvas` -- según la tab).
- Suite completa (frontend) en verde, sin hallazgos nuevos de lint/type-check, build sin errores.
- Verificación visual en vivo: la fila superior ya no desborda, en ninguna de las dos tabs.

## Hecho

Implementado y verificado en vivo.

- `MobEditor.vue`: `topMenuItems` (computed, reactivo al `activeSaveState`/`activeCanSave`) + `handleTopMenuAction(key)` reemplazan los 3 `GButton` sueltos por un `<GMenu :items="topMenuItems" label="Más acciones" @select="handleTopMenuAction" />`, sembrado junto al `GButton` de IA que queda como único control fuera del menú. Imports de `IconCamera`/`IconSave`/`IconExport` removidos (ya no se usan -- los ítems de `GMenu` son texto, mismo criterio que todo el resto de menús de la app).
- `MobEditor.spec.ts`: tests actualizados para abrir el menú "⋮" (escopado por `[aria-label="Más acciones"]`, no por `.g-menu__trigger` a secas -- ver memoria `ambiguous-selector-collision-pattern`) y verificar sus 3 ítems reales, en vez de buscar botones sueltos por texto. 16/16 tests en verde.
- **Verificación en vivo**: confirmado en ambas tabs (Modelo y Textura) -- la fila superior muestra solo el botón de IA + "⋮"; el menú abre con Reset cámara/Exportar/Guardar, ningún desborde.
- Estado final: 715/715 tests frontend en verde, `vue-tsc -b`/`eslint --max-warnings 0` sin hallazgos, `npm run build` sin errores.

**Post-076 (pedido explícito del PO -- "agregale iconos a esas opciones")**: `GMenu.vue` (el componente compartido, usado también por `ProjectCard.vue`/`MobCard.vue`/`ProjectDetail.vue`/etc.) gana un campo opcional `icon` por ítem (`GMenuItem.icon`), backward-compatible -- ningún consumidor existente que no lo pase cambia visualmente. Reset cámara/Exportar/Guardar ahora llevan el mismo ícono que tenían como `GButton` sueltos (`IconCamera`/`IconExport`/`IconSave`). Hallazgo real durante el test nuevo: bajo `shallowMount`, un ícono como `IconCamera` se stubea a pesar de listarlo en `stubs: {IconCamera: false}` porque INTERNAMENTE envuelve `IconBase` -- shallowMount stubea cualquier descendiente sin importar la profundidad, así que hubo que agregar también `IconBase: false` para que el `<svg>` real llegara al DOM en el test. Test nuevo en `GMenu.spec.ts` (ítem con `icon` lo renderiza, uno sin `icon` no rompe nada) y en `MobEditor.spec.ts` (los 3 ítems del menú muestran su ícono). Verificado en vivo. 717/717 tests frontend en verde, `vue-tsc -b`/`eslint --max-warnings 0` sin hallazgos, `npm run build` sin errores.

**Cierre**: mismo criterio que tickets anteriores -- PO autorizó el flujo git normal, consolidado en el PR #97 (`feat/071-076-rediseno-ui-inicio-editor-wizard`), mergeado a `dev` con CI verde y ambos Quality Gates de SonarQube (backend/frontend) verificados `OK` por SQL.
