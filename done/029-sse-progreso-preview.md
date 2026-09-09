# 029 — SSE de progreso con preview

**Milestone:** M4 · **Depende de:** 027, 028, 008 · **HUs:** HU-11 · **Épica:** 026

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §5 y Addendum de implementación). Implementar `GET /api/jobs/{jobId}/events` (SSE) mostrando las etapas de generación (Analizando referencia, Detectando silueta, Creando rig, Generando cuboides) y renderizando el modelo emergente en el viewport (008) mediante eventos de preview no persistente.

`preview_operations` es el mecanismo **preferido**; `preview_snapshot` queda permitido solo como resincronización/fallback — se evita transportar snapshots completos de forma repetida.

## Criterios de aceptación (TDD)
- Dado la generación en curso, cuando avanza cada etapa, entonces se emite un evento SSE con `stage`/`progress_pct` y, cuando aplica, `payload_jsonb` con `preview_operations` (preferido) o `preview_snapshot` (solo resincronización).
- Dado un evento de preview recibido, cuando se aplica al viewport, entonces **nunca** modifica `mob_drafts` ni crea `mob_revisions` — es descartable sin efecto en cualquier momento.
- Dado que el cliente se desconecta temporalmente y vuelve a conectar con `Last-Event-ID`, cuando reabre el SSE, entonces retoma los eventos desde donde se quedó (`ai_job_events.seq`).
- Dado que se cancela la generación, cuando se confirma, entonces el job se marca cancelado y el mob permanece sin draft ni revisión — los eventos de preview mostrados se descartan sin dejar rastro.

## Hecho

Implementado de punta a punta: backend (pipeline asíncrono + SSE + cancelación) y frontend (wizard paso 3 real, viewport de preview no persistente). Los 4 AC se cumplen todos, verificados tanto por tests automatizados como en vivo contra la API real de Anthropic.

### Backend

- **`MobGenerationService` reescrito** (028 era síncrono): `startGeneration(mobId)` hace el preflight (mob/referencia deben existir) y crea la fila `ai_jobs` (`status='running'`) DE INMEDIATO, antes de llamar a ningún proveedor de IA — `provider`/`model`/`prompt_version`/`schema_version` arrancan en un placeholder `"pending"` (columnas `NOT NULL`, ver 003) y se sobreescriben con la respuesta real en cuanto llega. Esto cierra el hueco que el propio ticket 028 dejó documentado ("un fallo de red nunca deja fila en `ai_jobs`") — ahora sí queda una fila real incluso si el fallo es de red, antes de cualquier respuesta.
- El pipeline real (`runPipeline`) corre en `generationExecutor` (`ThreadPoolTaskExecutor`, `GenerationExecutorConfig`), FUERA de cualquier transacción larga — cada escritura a `ai_jobs`/`ai_job_events` es su propia transacción corta (llamada directa a los repositorios de Spring Data, sin `@Transactional` envolvente). Esto también elimina el gotcha de `noRollbackFor` del ticket 028 (ya no hay una transacción larga que una excepción pueda marcar rollback-only).
- **`GeometryPlannerService` partido** en `requestOperations` (llama al proveedor, deserializa/whitelistea) + `applyOperations` (aplica con UV, sin volver a llamar al proveedor) — `plan()` los compone igual que antes (100% compatible con los tests del 028). El pipeline asíncrono usa ambos: `requestOperations` una vez, y reproduce las mismas operaciones ya obtenidas de forma incremental para el preview, sin gastar una segunda llamada real a la API.
- **Preview incremental, genérico sobre las 9 operaciones (005)**: `GenerationPreviewDiff.diff(antes, después)` compara dos `MobProjectModel` (aplicando prefijos crecientes del batch vía `GeometryEngine.apply`, sin UV — barato) y devuelve solo lo que cambió: bones/cuboids agregados o actualizados (ya resueltos, sin `tempId`) + ids de cuboids removidos. Nunca reinterpreta cada tipo de operación por separado — compara por id, así que `resizeCuboid`/`moveCuboid`/`rotateCuboid`/`setBonePivot`/`setBoneRotation`/`parentBone` quedan cubiertas con el mismo código que `createBone`/`createCuboid`.
- **`GET /api/jobs/{jobId}/events`** (`GenerationJobController`, `SseEmitter`): suscribe al broadcaster ANTES de leer el backlog persistido (`ai_job_events`, orden deliberado — ver comentario en el código: prefiere un duplicado posible en una ventana angosta antes que perder un evento; el cliente deduplica por `seq`). `Last-Event-ID` (AC #3) se lee del header estándar de reconexión SSE — el navegador lo reenvía solo, sin código de reconexión manual en el frontend.
- **Cancelación honesta** (`GenerationCancellationRegistry`, en memoria): revisada en puntos de control entre etapas, nunca interrumpe una llamada HTTP a IA ya en vuelo — documentado explícitamente como limitación real, no una promesa de cancelación instantánea.
- 34 tests backend nuevos/reescritos (`GenerationPreviewDiffTest`, `GenerationJobControllerTest`, `MobGenerationServiceTest` reescrito para el flujo async con sondeo acotado de `ai_jobs` en vez de sleeps fijos o un executor síncrono especial de test — el mismo `ThreadPoolTaskExecutor` real de producción se usa también en tests). **171 tests backend en total, 0 fallos.**

### Frontend

- **`GenerationStep.vue` (paso 3 del wizard) real**: dispara `POST /api/mobs/{mobId}/generate`, abre `EventSource` sobre `GET /api/jobs/{jobId}/events`, muestra las 4 etapas reales con progreso/mensaje en vivo, aplica cada `preview_operations` a un modelo de preview en memoria (`generationEvents.ts`, función pura `applyPreviewDelta`) y lo renderiza en `GenerationPreviewViewport.vue`. Deduplica eventos por `seq` (mitiga la ventana de duplicado documentada en el backend). Botón "Cancelar" con confirmación en dos pasos (mismo patrón que el borrado en cascada de `HierarchyBoneNode.vue`, 018 — nunca `confirm()` nativo).
- **`GenerationPreviewViewport.vue` (nuevo)**: envuelve `ThreeViewportService` DIRECTO, sin pasar por `useDraftModelStore` — decisión de diseño deliberada (AC #2: el preview nunca debe tocar el store real del editor manual, que es lo que persiste `mob_drafts`/`mob_revisions`). De solo lectura, sin selección ni gizmos.
- Al completar, la pantalla muestra un aviso honesto de que conectar la propuesta real con la pantalla de Resultado es alcance del ticket 030 (mismo límite ya establecido para `ResultStep.vue` desde el 027) — no navega sola.
- 52 tests frontend nuevos (`GenerationStep.spec.ts`, `GenerationPreviewViewport.spec.ts`, `generationEvents.spec.ts`, `generationApi.spec.ts`, más los ajustes a `AiMobWizard.spec.ts` para pasar por el `GenerationStep` ahora real). **260 tests frontend en total, 0 fallos.** `npm run lint`/`vue-tsc -b`/`npm run build` todos en verde.

## Verificación en vivo (Claude in Chrome, backend+Postgres+MinIO reales)

**Camino feliz, contra la API REAL de Anthropic** (`AI_VISION_PROVIDER=claude`/`AI_REASONING_PROVIDER=claude`, imagen real `carcomido_reference.png`): click "Generar con IA" desde el wizard real → las 4 etapas avanzaron en vivo, con el mensaje real de Claude ("Silueta detectada: Zombi humanoide encorvado, ropas desgarradas colgando, con grietas de energía púrpura recorriendo el cuerpo") → el rig se construyó incrementalmente, cuboid por cuboid, en el viewport 3D real a medida que llegaban los eventos `preview_operations` → "Generación completada." Confirmado en Postgres: `ai_jobs.status='completed'`, `provider='claude'`, `model='claude-sonnet-5'`, 29 filas en `ai_job_events` con `seq` estrictamente secuencial (1→29, sin huecos), terminando en `completado`/100/`preview_snapshot` — 28 operaciones reales aplicadas incrementalmente, un rig sustancialmente más rico que cualquier fixture de test.

**Cancelación en vivo**: nueva generación, click "Cancelar" mientras la llamada real al `StructuredReasoningProvider` seguía en vuelo (confirmado por los logs del backend) → confirmación explícita ("¿Cancelar la generación en curso?") → "La generación fue cancelada por el usuario." Confirmado en Postgres: `ai_jobs.status='cancelled'`, `proposal_jsonb IS NULL`, `finished_at` seteado — ningún draft/revisión tocado en ningún momento (AC #4 cumplido con evidencia real, no solo con el test).

Sin errores de consola en ningún punto de la verificación.

## Checklist de criterios de aceptación

- ✅ Eventos SSE con `stage`/`progress_pct` en cada avance, `payload_jsonb` con `preview_operations` (preferido, todo el camino) o `preview_snapshot` (solo el evento `completado`, resincronización) — verificado por test y en vivo.
- ✅ El preview nunca toca `mob_drafts`/`mob_revisions` — arquitectónicamente imposible (`GenerationPreviewViewport.vue` no usa `useDraftModelStore`; `MobGenerationService` nunca escribe esas tablas).
- ✅ Reconexión con `Last-Event-ID` retoma desde `ai_job_events.seq` — verificado por test HTTP (`GenerationJobControllerTest`).
- ✅ Cancelación marca el job `cancelled`, sin draft/revisión, preview descartado — verificado por test y en vivo contra la API real.
