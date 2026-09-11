# 069 — Asistente IA como drawer compartido en Modelo y Textura

## Objetivo
Homologar la última pieza pendiente del rediseño de shell (`done/068-homologacion-shell-modelo-textura.md`, deliberadamente dejada fuera de ese ticket): que el botón "Asistente IA"/"Generar con IA" abra el **mismo drawer lateral compartido** en las dos tabs del editor de mob, en vez del comportamiento actual divergente (panel embebido en Modelo vs. navegación a pantalla completa en Textura). Referencia visual: el mockup aprobado en `https://claude.ai/code/artifact/be368ed5-93e4-404b-a33e-86d58eff7026`.

VoBo del PO (2026-09-11) sobre dos decisiones de alcance:
- El contenido de `TextureAiGeneratorScreen.vue` no solo se extrae mecánicamente: se **rediseña ligeramente** para verse coherente dentro de un drawer angosto (hoy es pantalla completa).
- La ruta `/projects/:projectId/mobs/:mobId/texture/generate-ai` se **elimina** — el drawer la reemplaza por completo, sin dejarla como fallback.

## Alcance

### Incluye
- Nuevo `GDrawer.vue` en el design system: componente genérico y reusable (abrir/cerrar, cerrar con Escape, cerrar con click en backdrop, manejo de foco), con sus propios tests unitarios independientes del caso de uso de IA.
- Extraer el contenido de `AiEditPanel.vue` (Modelo, ~286 líneas, ya emite eventos) a una pieza embebible dentro del drawer.
- Extraer **y** ajustar visualmente el contenido de `TextureAiGeneratorScreen.vue` (Textura, ~965 líneas, pipeline SSE completo) para que quepa en el ancho del drawer, sin overflow horizontal ni scroll lateral — sin tocar la lógica de streaming/progreso en sí.
- `MobEditor.vue`: unificar `handleIaButtonClick` para que ambas tabs abran el mismo `GDrawer` compartido, con el contenido correspondiente según `activeTab`.
- Eliminar la ruta `/projects/:projectId/mobs/:mobId/texture/generate-ai` y cualquier referencia de navegación a ella.
- Actualizar/adaptar los tests existentes de `AiEditPanel.vue` y `TextureAiGeneratorScreen.vue` al nuevo contexto de montaje (embebidos en el drawer), más tests nuevos en `MobEditor.spec.ts` para la delegación compartida desde ambas tabs.
- Doc: addendum en `docs/ARQUITECTURA.md`.

### No incluye
- Cambios a la lógica de streaming SSE / contrato del backend del Geometry Planner o de generación de textura — este ticket es un cambio de contenedor/presentación, no de pipeline.
- Cambios a la lógica de negocio propia de `AiEditPanel.vue` (edición de huesos/joints por IA) — solo se reubica.
- Extender el patrón de drawer a otras features futuras más allá de esta consolidación.

## Criterios de aceptación (TDD)
- Clic en "Asistente IA" desde Modelo abre el `GDrawer` compartido con el contenido de `AiEditPanel` (tests existentes adaptados, comportamiento funcional sin regresión).
- Clic en "Generar con IA" desde Textura abre el **mismo** componente `GDrawer` (no navega de ruta) con el contenido de generación de textura por IA.
- La ruta `/projects/:projectId/mobs/:mobId/texture/generate-ai` ya no existe en el router — test que confirma que no está registrada / no renderiza la pantalla vieja.
- `GDrawer.vue` tiene tests unitarios propios: abre/cierra, Escape cierra, click en backdrop cierra, foco se mueve al contenido al abrir.
- El contenido de `TextureAiGeneratorScreen` embebido en el drawer no produce overflow horizontal ni scroll lateral en ningún paso del flujo (referencia, paleta, nivel de detalle, progreso, preview/diff), preservando el 100% del comportamiento de streaming ya existente.
- Todas las suites de test de `AiEditPanel`/`TextureAiGeneratorScreen` pasan adaptadas al nuevo contexto de montaje, más los tests nuevos de delegación compartida en `MobEditor.spec.ts`.
- Verificación en vivo contra `studio-dev`: ambas tabs abren el mismo chrome de drawer (mismo ancho, misma animación de apertura/cierre, mismo tratamiento de header) con el contenido correspondiente a cada tab.

## Hecho

VoBo del PO sobre el mockup interactivo (`https://claude.ai/code/artifact/a3514398-4fc4-430f-96b2-40299c0038a4`, *"doy vobo asi como esta, esta perfectisimo, que quede exactamente igual"*), implementado a partir de ahí.

**Componentes nuevos:**
- `GDrawer.vue` (design system): drawer lateral genérico sobre `<dialog>` nativo (mismo criterio que `AppDialog.vue` -- foco/tab-trap/Escape gratis del navegador, incluyendo mover el foco al contenido al abrir vía `showModal()`, comportamiento nativo, no testeado por separado igual que en `AppDialog.spec.ts`). Ancla a la derecha, ancho `min(420px, 92vw)`, desliza al abrir/cerrar (`transform: translateX`, 240ms). El cierre (Escape/backdrop/botón ×) anima la salida ANTES de emitir `cancel` -- resuelto con un `setTimeout` (no `transitionend`, poco confiable en jsdom/interrupciones/`prefers-reduced-motion`) que respeta `prefers-reduced-motion` (cierre inmediato). Slots: default (body con scroll propio) + `footer` opcional + `title-icon`. 8 tests propios.
- `TextureAiGeneratorPanel.vue` (`src/ai/texture/`): reemplaza a `TextureAiGeneratorScreen.vue` (pantalla de ruta, RETIRADA junto con su ruta `/projects/:projectId/mobs/:mobId/texture/generate-ai`). Mismo pipeline/lógica de streaming SSE, sin ningún cambio -- lo que cambió es el contenedor: de 2 columnas (~900px) a 1 sola columna (420px), reordenado como formulario (Referencia → Estilo → Detalle → Parte a generar → Generar), matching el mockup. Ya no hace `getMob`/`getDraft` propios (llegan como props `mobId`/`model`, el mismo `draft.model` que `MobEditor.vue` ya tenía cargado) -- el único fetch propio que queda es `listReferenceImages` (no viaja en el draft). `apply()` ya no navega (no hay ruta): emite `applied` y vuelve al formulario, mismo criterio que `AiEditPanel.vue` (el drawer se queda abierto, listo para otra generación).

**Componentes ajustados:**
- `MobEditor.vue`: `showAiPanel` (toggle inline, solo Modelo) se reemplaza por `showAiDrawer` (un solo flag compartido). `handleIaButtonClick` ahora solo abre el drawer; el contenido embebido (`AiEditPanel`/`TextureAiGeneratorPanel`) se decide según `activeTab`, mismo criterio que `handleTopSave`. Nuevo `handleTextureAiApplied`: reobtiene el draft (el atlas persistido no viaja en su JSON) y le pide a `TextureCanvas.reloadAtlas()` que lo recargue.
- `TextureCanvas.vue`: nuevo `reloadAtlas()` expuesto vía `defineExpose`, reusa `loadModelAtlas` tal cual -- sin duplicar la lógica de descarga/decodificación del atlas.
- `AiEditPanel.vue`: se le saca su propio `<h3>Asistente IA</h3>` (el título ahora lo pone `GDrawer`, para no duplicarlo) y el padding/scroll propio (ahora responsabilidad de `.g-drawer__body`). Sin cambios de lógica.
- `app/router.ts`: se retira la ruta `/projects/:projectId/mobs/:mobId/texture/generate-ai`.
- `sonar-project.properties`: la excepción S6819 de `TextureAiGeneratorScreen.vue` (ticket 067, radiogroups personalizados) migra de resourceKey al nuevo `TextureAiGeneratorPanel.vue` -- mismo markup, mismo hallazgo, solo cambió el nombre de archivo.

**Simplificación deliberada vs. el mockup (señalada, no oculta):** el mockup mostraba los botones Rechazar/Aplicar del resultado de Textura en un footer fijo separado del body con scroll. Se implementaron como parte del body scrolleable en vez de usar el slot `footer` de `GDrawer` -- levantarlos al footer hubiera requerido exponer el estado interno de fase/aplicando de `TextureAiGeneratorPanel` hacia `MobEditor.vue` para construir el footer externamente (acoplamiento evitable). Dado que el contenido del drawer rara vez necesita scroll en la práctica, la diferencia visual es mínima. Mismo criterio se aplicó a `AiEditPanel.vue` (nunca tuvo footer separado, ni lo pedía el ticket para ese componente).

**Hallazgo real encontrado en el camino (bug heredado, no introducido por este ticket):** cerrar el panel de IA de Modelo con un plan activo (ticket 031) dejaba `aiPreviewModel` sin resetear -- el canvas principal quedaba trabado mostrando `GenerationPreviewViewport` en vez de volver al `ThreeViewport` editable. Nunca se disparaba en la práctica (nadie cerraba el panel con un plan activo), pero al convertir el botón de IA en un drawer modal (cierre mucho más frecuente/esperado) se volvía mucho más alcanzable. Corregido en `closeAiDrawer()` ya que se estaba tocando este mismo wiring.

**TDD real**: `GDrawer.spec.ts` (8 tests, nuevo), `TextureAiGeneratorPanel.spec.ts` (16 tests, adaptado de `TextureAiGeneratorScreen.spec.ts` -- se eliminó el test de "mob inexistente", ya no aplica; "Aplicar exitoso" pasa de verificar navegación a verificar el emit `applied` + vuelta al formulario), `TextureCanvas.spec.ts` (1 test nuevo de `reloadAtlas`, limpieza del router-plugin muerto desde el 068), `MobEditor.spec.ts` (tests de "Asistente IA"/"Ticket 068" reescritos para el drawer compartido, 2 nuevos). Todos confirmados en rojo genuino antes de contarlos como ciclo TDD válido. Frontend completo: 613/613 tests, `vue-tsc -b` y `eslint --max-warnings 0` sin hallazgos, `npm run build` sin errores (el chunk propio de `TextureAiGeneratorScreen.vue` desaparece del build, confirmando que la ruta vieja quedó realmente retirada).

**Verificación en vivo contra `studio-dev`**: pendiente hasta que el PR se mergee y el pipeline redespliegue -- se hace y se documenta como paso final antes de mover este ticket a `done/`, mismo protocolo que todos los tickets anteriores de este proyecto.
