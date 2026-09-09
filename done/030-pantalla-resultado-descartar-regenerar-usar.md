# 030 — Pantalla Resultado: Descartar/Regenerar/Usar este modelo

**Milestone:** M4 · **Depende de:** 029, 020, 013 · **HUs:** HU-12 · **Épica:** 026

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (HU-12 y Diseño técnico §5 — "Usar este modelo" reutiliza el mecanismo de commit). Implementar la pantalla de Resultado con las tres acciones explícitas, reutilizando el mecanismo transaccional de Guardar (020) para crear la primera revisión, y mostrando el estado de compatibilidad real vía `FmmCompatibilityValidator` (013) — no un estimado.

## Criterios de aceptación (TDD)
- Dado que la generación termina exitosamente, cuando se llega a "Resultado", entonces se ve el modelo propuesto, conteo de cuboides/bones, y el estado de compatibilidad calculado corriendo 013 sobre la propuesta — el mob todavía no tiene ninguna revisión persistida.
- Dado "Resultado", cuando se ve la pantalla, entonces hay exactamente tres acciones: Descartar, Regenerar, Usar este modelo.
- Dado "Descartar", cuando se confirma, entonces la propuesta se abandona sin crear draft ni revisión.
- Dado "Regenerar", cuando se confirma, entonces se descarta la propuesta actual y se dispara un nuevo job (029).
- Dado "Usar este modelo", cuando se confirma, entonces se crean `mob_revisions.revision_number=1` y `mob_drafts.draft_version=1` en la misma transacción (mismo mecanismo que 020/031), `mobs.current_revision_number` pasa de 0 a 1, y se habilitan "Editar modelo"/"Exportar" — nunca ocurre un Apply implícito antes de este clic.

## Hecho

Implementado de punta a punta: backend (lectura de resultado + aceptación transaccional de la propuesta) y frontend (wizard paso 4 real, conectado automáticamente al completar el paso 3). Los 5 AC se cumplen todos, verificados tanto por tests automatizados como en vivo contra la API real de Anthropic.

### Decisión de alcance, VoBo explícito del PO (`AskUserQuestion`)

El AC dice que "Usar este modelo" debe habilitar "Editar modelo"/"Exportar" — pero ningún ticket anterior (015-020, el editor manual) montó una ruta real de edición para un mob existente; el editor solo se ejerció vía `/dev/viewport-harness` (harness de desarrollo, nunca un flujo productivo real). Se preguntó cómo resolver esta tensión; el PO eligió: **030 implementa las 3 acciones reales contra el backend, y al confirmar "Usar este modelo" navega de vuelta a `/projects/:id` (ya real, 022)** — "Editar modelo"/"Exportar" quedan como gap explícito y documentado (nunca un botón falso) hasta que un ticket futuro monte la ruta real del editor sobre un mob existente.

### Backend

- **`GET /api/jobs/{jobId}/result`** (`GenerationJobController`, nuevo `GenerationResultService`): deserializa `ai_jobs.proposal_jsonb` (nunca re-ejecuta `GeometryEngine`, ese trabajo ya está hecho desde 028), cuenta bones/cuboids, y calcula el estado REAL de compatibilidad FMM exportando la propuesta a `.bbmodel` (`BBModelExporterV5`, 010/011, con el mismo `UvLayoutStrategy` inyectado que el resto del proyecto) y corriéndole `FmmCompatibilityValidator` (013) encima — nunca un estimado.
- **`POST /api/jobs/{jobId}/apply`** ("Usar este modelo"): nuevo método `DraftPersistenceService.applyGenerationProposal` — a diferencia de `saveRevision` (020, que deliberadamente NUNCA toca `mob_drafts`), crea `mob_revisions` (`created_by='ai'`, valor ya contemplado por el `CHECK` de `mob_revisions` desde el ticket 003) Y `mob_drafts` en la MISMA transacción, avanzando `mobs.current_revision_number` — exactamente el AC. `createdBy="ai"` distingue esta revisión de una creada por "Guardar" (`"user"`).
- Ambos endpoints rechazan un job que no esté `completed` (`JobNotCompletedException`, 409 `JOB_NOT_COMPLETED`) — no hay ningún resultado que mostrar/aceptar mientras corre, ni si falló/se canceló.
- "Descartar"/"Regenerar" no necesitan ningún endpoint nuevo: Descartar es simplemente no llamar a `/apply` (nada se creó, nada que deshacer, satisface el AC trivialmente); Regenerar vuelve a llamar `POST /api/mobs/{mobId}/generate` (029), reutilizando la misma imagen de referencia ya subida en el paso 2 del wizard.
- 8 tests backend nuevos (`GenerationJobControllerTest` +6, `DraftPersistenceServiceTest` nuevo +2 cubriendo el rechazo de un modelo inválido). **179 tests backend en total, 0 fallos.**

### Frontend

- **`GenerationStep.vue`** ahora emite `completed(jobId)` al llegar al estado terminal exitoso — nunca hace el fetch del resultado ni navega por su cuenta, eso es responsabilidad de `AiMobWizard.vue`. El botón "Ir al proyecto" queda solo como escape hatch de fallido/cancelado (para completado, el wizard avanza automáticamente).
- **`ResultStep.vue`** pasa de shell a real: componente puramente presentacional (mismo criterio que `ConfigurationStep.vue`) — recibe todo por props (incluyendo el estado real de compatibilidad FMM y cada hallazgo, nunca un mensaje genérico) y solo emite intención (`discard`/`regenerate`/`apply`). Las 3 acciones piden confirmación en dos pasos antes de emitir (mismo patrón que el borrado en cascada de `HierarchyBoneNode.vue`, 018 — nunca `confirm()` nativo). `jobId` es la única prop opcional: sin ella, el componente asume que lo está montando `/dev/wizard-result-harness` (027) con datos de ejemplo y lo dice explícito en pantalla — con `jobId` real, ese aviso desaparece.
- **`AiMobWizard.vue`** orquesta el paso 4: al recibir `completed` de `GenerationStep`, llama `GET /api/jobs/{jobId}/result` y muestra `ResultStep` con el resultado real (con un estado de carga/error-con-reintento si el fetch falla, para no dejar al usuario sin salida si la generación fue exitosa pero leer su resultado falla). `discard` navega a `/projects/:id` sin llamar al backend. `regenerate` limpia el estado y vuelve al paso `'generation'` — `GenerationStep` se remonta y dispara un `POST /generate` nuevo solo. `apply` llama `POST /api/jobs/{jobId}/apply` y navega a `/projects/:id` si tiene éxito.
- `frontend/src/api/generationResultApi.ts` (nuevo): `getGenerationResult`/`applyGeneration`.
- 12 tests frontend nuevos/reescritos (`ResultStep.spec.ts` reescrito por completo para el componente real, `generationResultApi.spec.ts` nuevo, ajustes en `GenerationStep.spec.ts`/`AiMobWizard.spec.ts` para el nuevo flujo). **272 tests frontend en total, 0 fallos.** `npm run lint`/`vue-tsc -b`/`npm run build` todos en verde.

## Verificación en vivo (Claude in Chrome, backend+Postgres+MinIO reales, API REAL de Anthropic)

Generación real completa contra `carcomido_reference.png` (`AI_VISION_PROVIDER=claude`/`AI_REASONING_PROVIDER=claude`) → avance **automático** a "Resultado" (sin ningún clic manual, el stepper marca los 4 pasos con ✓) con conteos reales (`Cuboides: 14, Bones: 7, Textura: 128×128`) y `Compatibilidad FMM: Compatible` (calculado en vivo exportando+validando, no un placeholder) → click "Usar este modelo" con confirmación explícita ("¿Usar este modelo? Se crea la primera revisión guardada del mob a partir de esta propuesta.") → navega de vuelta a `/projects/:id`, el mob nuevo ("Carcomido Resultado Real") visible en el grid real.

Confirmado en Postgres, los 3 avanzan juntos en una sola transacción:
- `mobs.current_revision_number = 1`
- `mob_revisions`: `revision_number=1`, `created_by='ai'`
- `mob_drafts`: `draft_version=1`

Sin errores de consola en ningún punto de la verificación.

## Checklist de criterios de aceptación

- ✅ Al llegar a "Resultado" tras una generación exitosa, se ve el modelo propuesto (conteos reales) y el estado de compatibilidad calculado corriendo 013 sobre la propuesta — sin ninguna revisión persistida todavía (verificado: `mobs.current_revision_number` sigue en 0 hasta el clic de "Usar este modelo").
- ✅ Exactamente 3 acciones: Descartar, Regenerar, Usar este modelo.
- ✅ Descartar: la propuesta se abandona sin crear draft ni revisión (arquitectónicamente garantizado — no existe ningún endpoint de "discard" que pudiera crear algo).
- ✅ Regenerar: descarta el estado local y dispara un nuevo job (029) reutilizando la misma referencia.
- ✅ Usar este modelo: `mob_revisions.revision_number=1` + `mob_drafts.draft_version=1` en la misma transacción, `mobs.current_revision_number` 0→1 — verificado por test Y en vivo contra la API real. "Editar modelo"/"Exportar" quedan como gap documentado (VoBo del PO), no un botón falso.
