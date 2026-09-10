# 038 — Bugfix del progreso IA (SSE + Job State + UI)

## Objetivo

El paso "Generación" del wizard IA se reportó "pegado" en 0%/"Analizando
referencia..." sin avance real. Diagnóstico end-to-end realizado ANTES de
escribir código (con evidencia real, no supuestos):

- **Backend emite eventos reales** -- verificado leyendo `GenerationStage.java`/
  `GenerationEventView.java`/`MobGenerationService.java`: los stage-keys
  (`analizando_referencia`, `detectando_silueta`, `creando_rig`,
  `generando_cuboides`, `completado`/`fallido`/`cancelado`) y los campos JSON
  (`seq`, `stage`, `message`, `progressPct`, `payload`) coinciden EXACTO con lo
  que ya consume `GenerationStep.vue` (`STAGE_ORDER`/`outcomeForStage`). Sin
  mismatch de nombres -- esa hipótesis del reporte original queda descartada.
- **SSE entrega en tiempo real** -- reproducido localmente el vhost real de
  nginx (`deploy/vm-infra/nginx/galgoth-studio.conf`) con un backend SSE de
  prueba: los eventos llegan sin buffering. Hipótesis de nginx descartada.
- **No hay Pinia acá** -- `GenerationStep.vue` usa `ref()`s planos, no un
  store. El concern de `storeToRefs()` del reporte original no aplica.
- **Causa real, confirmada con los 2 jobs reales completados en `dev` el
  2026-09-09 contra Claude de verdad** (`ai_job_events`, timestamps reales):
  el pipeline hace UNA sola llamada bloqueante real a Claude para el Geometry
  Planner (`GeometryPlannerService.requestOperations`) que tarda 70-90s en
  producción, **sin emitir ningún evento mientras espera** -- después, como
  la respuesta ya llegó completa, `replayOperationsWithPreview` reproduce
  ~30 operaciones en memoria en <300ms. Resultado real observado: pantalla
  congelada ~80s en "Detectando silueta" y después todo de golpe. Nunca se
  detectó antes porque el provider mock resuelve ambas llamadas de IA en
  <100ms (el hueco es invisible ahí), y la técnica de parchear `EventSource`
  usada en la verificación de ticket 037 sumó demora artificial SOBRE un
  burst ya instantáneo, sin ejercitar nunca el hueco real.

Este ticket corrige el hueco real (no una simulación de UI) y agrega las dos
etapas reales que faltaban, con decisión explícita del PO (VoBo vía
AskUserQuestion, 2026-09-09):

## Decisiones de diseño (con VoBo del PO)

1. **Streaming real de la respuesta de Claude para el Geometry Planner**
   (opción elegida sobre el heartbeat simple) -- la respuesta de Anthropic se
   consume vía `"stream": true`, parseando el array JSON de operaciones de
   forma incremental: cada operación real que el modelo termina de emitir se
   aplica y se manda como evento `preview_operations` real, con progreso
   basado en operaciones-vistas-hasta-ahora (no en timers).
2. **Switch operativo `AI_GEOMETRY_STREAMING_ENABLED`** (default `true`) --
   si el streaming empieza a fallar mucho en producción, se apaga con esta
   env var + redeploy (sin cambio de código) y el pipeline cae al modo
   heartbeat: la llamada bloqueante de hoy, con un ping periódico honesto
   (mismo stage/%, mensaje con tiempo transcurrido) mientras espera --
   nunca inventa avance de % ni de etapa. Es un switch manual, no un
   fallback automático por request fallido (para no enmascarar fallas
   reales).
3. **Las 2 etapas nuevas se insertan en el pipeline real, antes de
   `completado`**:
   - `validando_geometria` -- la validación FMM (`FmmCompatibilityValidator`)
     se corre DENTRO del pipeline en vez de recién en `GET /result`. Sigue
     siendo informativa (no hace fallar el job si hay issues) -- solo se
     hace visible como etapa real, mismo comportamiento de aceptación que
     hoy.
   - `preparando_resultado` -- envuelve la aplicación final con UV
     (`GeometryPlannerService.applyOperations`), hoy silenciosa.
4. **Copy de los submensajes contextuales por etapa**: redactado por el
   equipo (el PO no tenía el texto literal guardado, solo el mockup 03 con
   las etiquetas de etapa) -- tono consistente con el mockup ("Esto puede
   tardar unos segundos...", "La IA está construyendo el modelo en tiempo
   real..."). Sujeto a aprobación final del PO junto con las capturas.

## Criterios de aceptación (TDD)

- [ ] `StreamingOperationsParser` (nuevo): unit tests que alimentan
  fragmentos de texto (deltas SSE de distinto tamaño, incluso partidos a
  mitad de un token) y verifican que emite cada `GeometryOperation` completa
  en el momento correcto, tolera espacios/saltos de línea entre elementos, y
  no emite nada hasta tener un objeto completo.
- [ ] `ClaudeMessagesClient`: nuevo método de streaming real contra la API de
  mensajes de Anthropic (`stream: true`), con test que mockea la respuesta
  SSE y verifica que los deltas de texto llegan incrementalmente al callback.
- [ ] `MobGenerationService`: con streaming habilitado, cada operación
  parseada dispara un evento `preview_operations` real (no un replay
  post-hoc) -- test de integración verificando orden y progreso creciente.
- [ ] Con `AI_GEOMETRY_STREAMING_ENABLED=false`, el pipeline cae al modo
  heartbeat: test verificando que se emiten eventos periódicos con el mismo
  stage/% mientras la llamada bloqueante está en curso.
- [ ] `validando_geometria`/`preparando_resultado` aparecen como eventos
  reales antes de `completado`, con FMM seguir siendo informativo (un job con
  issues FMM sigue llegando a `completado`).
- [ ] Frontend: `GenerationStep.vue` incluye las 6 etapas reales en
  `STAGE_ORDER` con su submensaje contextual, estados PENDING/ACTIVE/
  COMPLETED/ERROR derivados 100% de eventos reales.
- [ ] Estados explícitos de error, cancelación, reconexión SSE, y
  recuperación tras refresh de página (recupera `jobId`, reconecta sin
  reiniciar desde 0% ni duplicar el job).
- [ ] Tests nuevos: mapeo de etapas (unit), cliente SSE (integración),
  reconexión, error a mitad de stream (en `generando_cuboides`/64%),
  cancelación a mitad de stream (en `generando_cuboides`).
- [ ] Logging de debug estructurado, solo en dev (`[AI JOB] connected
  jobId=...`, `[AI JOB EVENT] seq=... stage=... progress=...`), nunca en
  producción.
- [ ] Prueba manual real: una generación completa de Carcomido contra Claude
  real en `dev`, con capturas mostrando múltiples actualizaciones
  intermedias reales (no solo 0%→100%).
- [ ] `docs/API.md`/`docs/COMPONENTES.md` actualizados con el nuevo
  contrato de eventos (2 stages nuevas) y el switch operativo.

## Hecho
(se completa al cerrar el ticket)
