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

- [x] `StreamingOperationsParser` (nuevo): unit tests que alimentan
  fragmentos de texto (deltas SSE de distinto tamaño, incluso partidos a
  mitad de un token) y verifican que emite cada `GeometryOperation` completa
  en el momento correcto, tolera espacios/saltos de línea entre elementos, y
  no emite nada hasta tener un objeto completo.
- [x] `ClaudeMessagesClient`: nuevo método de streaming real contra la API de
  mensajes de Anthropic (`stream: true`), con test que mockea la respuesta
  SSE y verifica que los deltas de texto llegan incrementalmente al callback.
- [x] `MobGenerationService`: con streaming habilitado, cada operación
  parseada dispara un evento `preview_operations` real (no un replay
  post-hoc) -- test de integración verificando orden y progreso creciente.
- [x] Con `AI_GEOMETRY_STREAMING_ENABLED=false`, el pipeline cae al modo
  heartbeat: test verificando que se emiten eventos periódicos con el mismo
  stage/% mientras la llamada bloqueante está en curso.
- [x] `validando_geometria`/`preparando_resultado` aparecen como eventos
  reales antes de `completado`, con FMM seguir siendo informativo (un job con
  issues FMM sigue llegando a `completado`).
- [x] Frontend: `GenerationStep.vue` incluye las 6 etapas reales en
  `STAGE_ORDER` con su submensaje contextual, estados PENDING/ACTIVE/
  COMPLETED/ERROR derivados 100% de eventos reales.
- [x] Estados explícitos de error, cancelación, reconexión SSE, y
  recuperación tras refresh de página (recupera `jobId`, reconecta sin
  reiniciar desde 0% ni duplicar el job).
- [x] Tests nuevos: mapeo de etapas (unit), cliente SSE (integración),
  reconexión, error a mitad de stream (en `generando_cuboides`/64%),
  cancelación a mitad de stream (en `generando_cuboides`).
- [x] Logging de debug estructurado, solo en dev (`[AI JOB] connected
  jobId=...`, `[AI JOB EVENT] seq=... stage=... progress=...`), nunca en
  producción.
- [x] Prueba manual real: una generación completa de Carcomido contra Claude
  real en `dev`, mostrando múltiples actualizaciones intermedias reales
  (no solo 0%→100%) -- ver evidencia abajo.
- [x] `docs/API.md`/`docs/COMPONENTES.md` actualizados con el nuevo
  contrato de eventos (2 stages nuevas) y el switch operativo.

## Hecho

Cerrado en 2 PRs sobre `dev` (nunca `qa`/`prod`, instrucción vigente):
**#47** (fix principal) y **#48** (2 hallazgos reales encontrados en la
verificación en vivo de #47, corregidos antes de cerrar por decisión
explícita del PO).

### #47 -- fix principal

- Backend: `StreamingOperationsParser` (nuevo, parser incremental del array
  JSON de operaciones), `ClaudeMessagesClient.callWithTextStreaming` (SSE
  real de Anthropic, filtra `thinking_delta`), `StructuredReasoningProvider
  .reasonStreaming` (default + override real en `ClaudeReasoningProvider`),
  `GeometryPlannerService.planStreaming`, `MobGenerationService` reestructurado
  (switch `AI_GEOMETRY_STREAMING_ENABLED`, modo streaming real vs. heartbeat,
  2 etapas nuevas `preparando_resultado`/`validando_geometria` antes de
  `completado`).
- **Bug real encontrado y corregido en el camino** (no en el plan original):
  `StreamingOperationsParser.emitElement()` envolvía en un mismo `try/catch`
  tanto el parseo JSON como la llamada al callback del caller -- una
  `GenerationCancelledException` real lanzada desde `MobGenerationService`
  durante una cancelación a mitad de stream quedaba disfrazada como
  "operación inválida" (`StreamingOperationParseException`), convirtiendo una
  cancelación real en un fallo. Atrapado por el propio test de cancelación
  a mitad de stream, corregido separando el parseo (con su catch) de la
  llamada al callback (sin catch, deja propagar cualquier excepción del
  caller tal cual).
- Frontend: `generationStages.ts` (nuevo, mapeo de etapas extraído a módulo
  testeable), `GenerationStep.vue` (6 etapas reales, submensaje contextual
  por etapa -- copy redactada por el equipo, el PO no tenía el texto literal
  guardado más allá de las etiquetas del mockup 03, sujeta a su aprobación
  si quiere ajustarla --, logging debug solo-dev, estado "Reconectando",
  "Reintentar" en fallo, recuperación de `jobId` vía `sessionStorage` tras
  refresh de página), `AiMobWizard.vue` (persiste `step`/mob en
  `sessionStorage` para que `GenerationStep` sobreviva un refresh real del
  navegador -- `'result'` queda sin esta recuperación, mismo comportamiento
  que antes de este ticket, fuera de alcance).
- Tests nuevos: `StreamingOperationsParserTest` (8), `MobGenerationServiceStreamingTest`
  (4, incluye error/cancelación a mitad de stream en `generando_cuboides`/64%),
  `MobGenerationServiceHeartbeatTest` (2), `generationStages.spec.ts` (20),
  +6 tests nuevos en `GenerationStep.spec.ts`. Suite completa verde: backend
  (`gradle test`) y frontend (342 tests, lint, `vue-tsc -b`, build).
- Sonar: el PR llegó a tener gate `ERROR` real (7 hallazgos backend + 2
  frontend, verificados vía `sonarqube-db`) -- 3 eran la palabra "todo"
  (español) chocando con la regla S1135 (TODO comment), un falso positivo
  recurrente de este codebase comentado en español; el resto, hallazgos
  reales menores (try-with-resources del `ScheduledExecutorService`,
  constante duplicada, patrón sin nombre, `Thread.sleep()` justificado en
  test, aserciones AssertJ sin encadenar). Los 9 corregidos, gate `OK` con
  0 violaciones nuevas antes de mergear.

### #48 -- hallazgos reales de la verificación en vivo (antes de cerrar)

Verificación manual mandatoria ejecutada contra `studio-dev` con Claude
real: generación completa de "Carcomido-verificacion-038" (job real
`cb867de9-ee79-40c4-9234-9c40ca542325`, 2026-09-10). **Resultado: completó
bien de punta a punta** (12 cuboides, 9 bones, FMM compatible), con
evidencia real de `ai_job_events` mostrando múltiples actualizaciones
intermedias reales (no un salto 0%→100%):

```
seq1     analizando_referencia            5%  t=0.0s
seq2     detectando_silueta              25%  t=6.4s
seq3     creando_rig                     41%  t=81.6s   <- Claude "pensando" 75s antes del primer token real
seq4-20  creando_rig/generando_cuboides  42%->58%  t=81.6s-81.75s (burst de 17 eventos en <130ms, streaming real)
seq21-32 generando_cuboides             59%->70%  t=85.5s (segundo burst de 12 eventos en ~103ms, 3.76s después del anterior)
seq33    preparando_resultado           90%  t=85.6s
seq34    validando_geometria            95%  t=85.7s
seq35    completado                    100%  t=85.7s
```

Esta misma corrida expuso 2 hallazgos reales, corregidos antes de cerrar
(decisión explícita del PO -- ver conversación):

1. **El heartbeat no cubría el "pensamiento" de Claude en modo streaming.**
   Los ~75s entre `detectando_silueta` y el primer token real de la
   respuesta (razonamiento extendido, `thinking_delta`, deliberadamente no
   mostrado) quedaban tan silenciosos como el bug original -- streaming
   solo ayuda una vez que hay contenido real que emitir. Fix: `planWithStreaming`
   ahora también corre el heartbeat honesto mientras no llegó ninguna
   operación real todavía (`AtomicBoolean`, visibilidad correcta entre
   hilos), apagándose apenas llega la primera operación real.
2. **`proxy_read_timeout` de nginx (default de fábrica, 60s, nunca
   configurado para este vhost) cortó la conexión SSE durante ese mismo
   silencio de 75s.** El cliente reconectó solo (el fix de reconexión de
   este mismo ticket, visible en pantalla como "Reconectando al proceso...")
   y la generación terminó bien igual vía el backlog completo -- pero
   depender de la reconexión en cada generación real es frágil. Fix:
   `proxy_read_timeout 300s;` en `deploy/vm-infra/nginx/galgoth-studio.conf`
   (margen real sobre los ~85s observados de una generación completa),
   confirmado aplicado en el vhost real de `dev` tras el redeploy
   (`grep proxy_read_timeout` sobre `/etc/nginx/sites-enabled/galgoth-studio.conf`
   en `ampere-free`).

Test nuevo verificando el heartbeat durante la espera del primer token en
modo streaming. Suite completa verde. Gate de Sonar `OK`/0 violaciones
nuevas en ambos proyectos antes de mergear #48.

### Redeploy real verificado

`dev` redesplegado automáticamente tras cada merge (Jenkins Shared
Library) -- confirmado `actuator/health` → `UP` y contenedor `healthy`
después de ambos merges. Nunca se tocó `qa`/`prod`, instrucción vigente
("por ahora no despliegues en qa, solo en dev").

### Pendiente / decisión del PO

- **Copy de los submensajes contextuales**: redactada por el equipo, no es
  el texto literal que el reporte original mencionaba (el PO no lo tenía
  guardado). Si el PO quiere ajustar el tono/texto exacto, es un cambio de
  copy puro, sin tocar lógica.
- El mob real de prueba "Carcomido-verificacion-038" quedó creado en el
  proyecto "Galgoth" de `dev` (mismo criterio que el resto de la
  verificación en vivo de este ciclo) -- se puede borrar si el PO lo
  prefiere, no se borró unilateralmente.
