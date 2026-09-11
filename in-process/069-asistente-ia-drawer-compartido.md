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
