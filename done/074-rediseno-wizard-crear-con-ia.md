# 074 — Rediseño del wizard "Crear con IA" (fidelidad visual estricta)

## Objetivo
Reconstruir los 4 pasos del wizard de generación de mobs con IA (`AiMobWizard.vue`, en `/projects/{id}/mobs/new-ai`: Referencia → Configuración → Generación → Resultado) para que coincidan visualmente con la referencia entregada por el Product Owner. Mismo flujo que los tickets 071-073: preview interactivo (Artifact) con VoBo del PO antes de implementar.

## Decisiones del Product Owner (previas a implementar, vía AskUserQuestion)
- **"Ejemplos de referencias"** (galería de 4 miniaturas + "Ver más ejemplos" en el paso Referencia): puramente decorativo. Ninguna miniatura es clickeable, "Ver más ejemplos" no lleva a ningún lado (no existe una pantalla de galería de ejemplos en el proyecto).
- **"Rig estimado"** (fila informativa del paso Configuración): dinámico según el "Tipo de entidad" elegido (`RIG_ESTIMATE_BY_TYPE`), no un texto fijo.
- **"Ver detalle técnico"** (link del paso Resultado en la referencia visual): se quita -- mismo criterio que "Todos" en el ticket 072, no mostrar un link que no lleva a ningún lado real; los mismos datos ya están en el resumen de al lado.

## Alcance

### Incluye
- **`ReferenceStep.vue`**: layout de dos columnas -- dropzone restilizado (ícono + botón "Seleccionar imagen" explícito, `GButton` variant `accent`, HERMANO del botón del dropzone, nunca anidado -- Sonar S6819) + sección decorativa "Ejemplos de referencias" (4 íconos ya existentes del design system, ninguno clickeable) + panel lateral "Consejos para mejores resultados" (3 tips + recuadro "Tip"). El comportamiento real de selección/validación de imagen no cambia.
- **`ConfigurationStep.vue`**: preview sigue mostrando la foto REAL subida (no un placeholder -- la referencia usaba uno solo por ser una vista estática); "Resolución de textura" pasa de `<select>` nativo a `GSelect.vue` (regla `no-native-form-controls`); los 5 botones de "Tipo de entidad" ahora llevan ícono (reutiliza los 5 íconos del ticket 073); contador de caracteres del nombre (0/32); fila informativa Rig estimado (dinámico)/Vista previa/Formato de salida.
- **`GenerationStep.vue`**: cada etapa de la lista muestra tiempo transcurrido (derivado de timestamps reales de los eventos SSE, nunca un `setInterval`) y una descripción distinta según esté en progreso (`hint`) o ya completada (`doneHint`, campo nuevo en `generationStages.ts`); "Registro en tiempo real" -- acumula el mensaje de cada evento SSE nuevo con hora local, tope de 50 líneas; badges "Polígonos (cuboides)"/"Huesos (rig)" sobre el preview (derivados de `previewModel`, mismo dato ya construido vía SSE); hints de interacción del viewport (Orbitar/Panorámica/Zoom, verificados contra el `OrbitControls` real); botón "Cancelar generación" de ancho completo con ícono.
- **`ResultStep.vue`**: pill flotante "Compatible con FMM"/"Con problemas" sobre el preview (además del que ya vive en el resumen); cada stat del resumen lleva su propio ícono; hints de interacción del viewport; las 3 acciones pasan de fila a columna de ancho completo (mismo comportamiento de confirmación, solo layout).
- **`WizardStepper.vue`**: el conector entre un paso completado y el siguiente se pinta en `--accent` (antes siempre `--border`).
- **Design system**: 3 íconos nuevos (`IconInfo`, `IconSun`, `IconBook`); `GSelect.vue` gana un slot opcional `#icon` (backward-compatible, sin efecto en los ~10 consumidores existentes que no lo usan).
- **`generationStages.ts`**: nuevo campo `doneHint` por etapa (descripción en pasado, mostrada una vez completada).

### No incluye
- Ningún cambio de contrato/endpoint del backend -- pasada 100% de presentación sobre datos que ya existían (`previewModel`, eventos SSE, `GenerationResult`).
- "Detectar tipo con IA" en Configuración -- sigue sin existir (ticket 028, fuera de alcance, ya documentado desde el ticket 027).
- Grid overlay/fullscreen en el viewport del paso Resultado -- la referencia visual los insinuaba como botones de toolbar, pero no hay ninguna función real detrás; se omiten en vez de agregar controles decorativos sin efecto (mismo criterio que "Ver detalle técnico").

## Criterios de aceptación (TDD)
- El comportamiento real de los 4 pasos (validación de imagen, creación del mob, subida de referencia, SSE de generación, aplicar/descartar/regenerar) no cambia -- ninguna regresión sobre `AiMobWizard.spec.ts`.
- "Rig estimado" cambia según el tipo de entidad elegido (5 valores, uno por tipo).
- El toolbar de Configuración no usa ningún `<select>` nativo.
- "Registro en tiempo real" muestra un renglón por evento SSE con mensaje (nunca duplicados ni vacíos); los badges de Polígonos/Huesos reflejan el `previewModel` real.
- `ResultStep.vue` mantiene exactamente 3 `<button>` (ningún control decorativo nuevo sin función real).
- Suite completa (frontend) en verde, sin hallazgos nuevos de lint/type-check, build sin errores.
- Verificación visual en vivo contra la referencia, en un viewport equivalente.

## Hecho

Implementado sobre el preview interactivo con VoBo del PO (`https://claude.ai/code/artifact/dadfa6ee-1be0-4812-bb21-e63ceee5a5cd`, *"esta perfecto, implementalo"*), con las 3 decisiones explícitas de producto aplicadas (ejemplos decorativos, rig dinámico, sin "ver detalle técnico").

**Design system:**
- `IconInfo.vue`/`IconSun.vue`/`IconBook.vue` -- 3 íconos nuevos, mismo patrón `IconBase.vue`.
- `GSelect.vue`: slot opcional `#icon` (ícono decorativo a la izquierda del valor elegido, dentro del trigger) -- verificado backward-compatible contra `GSelect.spec.ts` sin tocarlo.

**`ReferenceStep.vue`**: reescrito con layout de dos columnas. Hallazgo real durante la implementación: el dropzone completo era un solo `<button>`, y el nuevo botón "Seleccionar imagen" no podía vivir DENTRO de él (Sonar S6819 -- contenido interactivo dentro de un `<button>` es HTML inválido). Resuelto igual que `ProjectCard.vue`/`MobCard.vue`: el `<div>` contenedor lleva los handlers de drag&drop (no requieren semántica de botón), y adentro viven DOS `<button>` hermanos -- el trigger de click-to-open (icono+texto) y el `GButton` "Seleccionar imagen" explícito, ninguno anidado en el otro.

**`ConfigurationStep.vue`**: reescrito -- preview con la foto real + badge "Referencia seleccionada", contador de caracteres, íconos por tipo de entidad, `GSelect` para resolución de textura (con el nuevo slot `#icon`), fila informativa con `RIG_ESTIMATE_BY_TYPE` (5 valores, uno por `BaseType`).

**`generationStages.ts`**: agrega `doneHint` por etapa (6 valores nuevos, uno por etapa real) -- test nuevo verificando que no esté vacío y sea distinto del `hint` en progreso.

**`GenerationStep.vue`**: reescrito -- tiempo por etapa derivado de timestamps reales de los eventos SSE (`stageStartedAt`/`lastEventAt`, sin `setInterval`, decisión deliberada para no arriesgar timers colgados en los tests); "Registro en tiempo real" (tope 50 líneas); badges de Polígonos/Huesos; hints de interacción del viewport; botón "Cancelar generación" de ancho completo con ícono (mismo `GButton variant="danger"`, mismo comportamiento de confirmación).

**`ResultStep.vue`**: reescrito -- pill flotante de compatibilidad FMM sobre el preview, íconos por stat, hints de interacción, acciones en columna de ancho completo. Verificado con un test explícito que siguen siendo exactamente 3 `<button>` (ningún control decorativo sin función real se coló).

**`WizardStepper.vue`**: conector entre pasos completados en `--accent`.

Tests actualizados/nuevos en los 6 archivos tocados (`ReferenceStep`/`ConfigurationStep`/`GenerationStep`/`ResultStep`/`generationStages`/`GSelect`) -- 712/712 tests frontend en verde, `vue-tsc -b`/`eslint --max-warnings 0` sin hallazgos, `npm run build` sin errores.

**Verificación en vivo** (stack real, backend con `AI_*_PROVIDER=mock`): confirmados visualmente los 4 pasos -- Referencia (galería decorativa, panel de consejos, dropzone con botón explícito), Configuración (imagen real subida, contador de caracteres, "Rig estimado" cambiando en vivo de "Estándar (bipedal)" a "Radial (8 patas)" al elegir Arácnido), Generación y Resultado.

El paso Generación no se podía capturar en el primer intento -- el proveedor mock resuelve el pipeline completo en menos de un frame, demasiado rápido para un screenshot. Resuelto sin tocar ningún código real: se inyectó vía `javascript_tool` un `EventSource` que encola y despacha cada evento SSE con una demora artificial (1.8s entre eventos), puramente en la pestaña del navegador de esta verificación -- mismo criterio ya usado para verificar transiciones CSS en cámara lenta en tickets anteriores (memoria `claude-in-chrome-frozen-css-transition-gotcha`), ahora aplicado al *pacing* de eventos en vez de a una transición. Con eso confirmado en vivo: progreso al 41%, etapas con tiempo real por etapa (2s/2s/0s) y su descripción cambiando de "en progreso" a completada, "Registro en tiempo real" con timestamps y mensajes reales, badges "Polígonos (cuboides)"/"Huesos (rig)" reflejando el modelo en construcción, overlay "Esperando la primera geometría…", y los hints Orbitar/Panorámica/Zoom -- todo coincide con la referencia. Queda además cubierto por 18 tests unitarios de `GenerationStep.spec.ts` (5 nuevos de esta pasada: registro en tiempo real, `doneHint` por etapa, badges reactivos, botón de cancelar).

**Cierre**: mismo criterio que tickets 071-073 -- PO autorizó el flujo git normal, consolidado en el PR #97 (`feat/071-076-rediseno-ui-inicio-editor-wizard`), mergeado a `dev` con CI verde y ambos Quality Gates de SonarQube (backend/frontend) verificados `OK` por SQL.

**Hallazgo real durante la reconstrucción de commits (no de Sonar, de inspección propia)**: los comentarios de código de este ticket (`ReferenceStep.vue`/`ConfigurationStep.vue`/`GenerationStep.vue`/`ResultStep.vue`/`WizardStepper.vue`/`generationStages.ts`/`GSelect.vue` y sus tests) estaban todos etiquetados "Post-073" en vez de "Post-074" -- confusión con el ticket del detalle de proyecto, arrastrada probablemente desde el momento en que ambos tickets se trabajaron en secuencia sin commits intermedios que la hubieran expuesto antes. Corregido en los 13 archivos antes de commitear (nunca llegó a quedar en el historial de git).
