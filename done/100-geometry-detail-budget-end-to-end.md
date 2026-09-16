# 100 — Presupuesto `geometryDetail` conectado de punta a punta

## Objetivo
Nace de `docs/definiciones/anatomia-por-capas-generacion-mobs.md` (HU-4). Hoy no existe ningún control de detalle geométrico en la UI — el número de cuboides depende enteramente de lo que el LLM decida. Este ticket agrega un selector real (Simple/Detallado/Alto → `LOW`/`MEDIUM`/`HIGH`) en `ConfigurationStep.vue` que de verdad condiciona el presupuesto de cuboides secundarios (`SecondaryGeometryPlanner`, ticket 099), y muestra el conteo real en el resultado.

**Depende de:** 099 (el presupuesto alimenta al planner secundario). **No bloquea** a otros tickets de esta epic.

## Alcance
**Incluye:**
- `ConfigurationStep.vue`: selector funcional "Detalle geométrico" (default "Detallado" = `MEDIUM`), viaja en el payload de `POST /api/mobs/{mobId}/generate`.
- Presupuestos orientativos (no cuotas rígidas): `LOW` 8–18 cuboides, `MEDIUM` 18–45, `HIGH` 35–80 — instrucción real al `SecondaryGeometryPlanner`, no solo texto cosmético de prompt.
- `ResultStep.vue`: muestra el conteo real de cuboides generados (dato real, ya existente en el modelo, no mock).
- Si el LLM propone muy por debajo o por encima del presupuesto, no falla el job — se registra como `generationWarning` informativo.

**No incluye:**
- Resolución de textura (ticket 103, selector distinto en la misma pantalla).
- Cambiar el layout general de `ConfigurationStep.vue` más allá de agregar este control.

## Criterios de aceptación (TDD)
- Dado que selecciono "Alto" en Configuración, cuando genero, entonces el presupuesto 35–80 llega al backend y condiciona las operaciones secundarias propuestas.
- Dado un resultado generado, cuando reviso Resultado, entonces veo el conteo real de cuboides (no un valor fijo/mock).
- Dado que el LLM propone muy por debajo o por encima del presupuesto, cuando termina la generación, entonces el job no falla — queda como `generationWarning`.
- Test de integración frontend: cambiar el selector cambia el payload real enviado a la API (no solo el estado visual del componente).

## Hecho
- `GeometryDetail` (backend, `domain/model/`, nuevo enum): `LOW`(8-18)/`MEDIUM`(18-45)/`HIGH`(35-80) — presupuestos ORIENTATIVOS de cuboides totales (primaria+secundaria). `secondaryBudget(primaryCount)` calcula el presupuesto real para `SecondaryGeometryPlanner` de forma dinámica (extremo superior menos lo que la anatomía primaria ya cubre, nunca negativo) — generaliza correctamente a futuros templates (097 iteración 2), no un número hardcodeado por nivel. `isWithinBudget(total)` para el chequeo final. 4 tests nuevos.
- `POST /api/mobs/{mobId}/generate` acepta body opcional `{"geometryDetail":"LOW"|"MEDIUM"|"HIGH"}` (`StartGenerationRequest`, nuevo) — sin body, o con el campo ausente/null, usa `MEDIUM` (compatibilidad total con el contrato anterior a este ticket). `docs/API.md` actualizado.
- `MobGenerationService.startGeneration`: nuevo overload de 2 argumentos (`mobId`, `geometryDetail`); el de 1 argumento delega a `MEDIUM` — cero cambio para callers existentes (incluidos los ~15 call sites de test). `GenerationJobContext` lleva el campo de punta a punta hasta `runPipeline`, que calcula el presupuesto secundario real ANTES de invocar `SecondaryGeometryPlanner` (ya no `SecondaryGeometryPlanner.DEFAULT_SECONDARY_BUDGET`, que se eliminó por quedar sin uso — dejarlo habría sido código muerto y engañoso, su propio Javadoc decía "mientras no existe todavía un selector conectado", ya no es cierto).
- Advertencia (no falla el job, HU-4): si el conteo final de cuboides queda fuera de `[minTotalCuboids, maxTotalCuboids]` del nivel elegido, se loguea con `log.info` (mismo patrón que `logRejections`/`logGenerationWarnings` del ticket 099).
- `ConfigurationStep.vue`: selector real "Detalle geométrico" (Simple/Detallado ✓ default/Alto → LOW/MEDIUM/HIGH), viaja en el `confirm` emitido. `AiMobWizard.vue` lo retiene en memoria (no necesita sobrevivir a un refresh de página, a diferencia de `createdMob` — una reconexión SSE tras refresh no vuelve a llamar `POST /generate`) y lo pasa a `GenerationStep.vue`, que lo manda en `startGeneration(mobId, geometryDetail)`.
- `ResultStep.vue` **ya mostraba el conteo real** de cuboides/bones (`generationResult.cuboidCount`/`.boneCount`, alimentado por `GET /api/jobs/{jobId}/result`) — verificado leyendo el código real antes de asumir que hacía falta trabajo acá (los `27`/`7` que aparecían eran solo defaults de prop de Vue, nunca usados en el flujo real de `AiMobWizard.vue`). Ningún cambio necesario para ese criterio de aceptación.
- Tests: 4 nuevos backend (`GeometryDetailTest`) + 1 nuevo frontend (`ConfigurationStep.spec.ts`: elegir "Alto" cambia el `geometryDetail` del confirm emitido, con selector scopeado por `aria-label` para no repetir el hallazgo de colisión de selectores ambiguos entre los 2 `GSelect` de esta pantalla) + 3 tests existentes actualizados a la nueva forma del payload/llamada.
- Suite completa: backend **529/529 en verde**, frontend **912/912 en verde**, `vue-tsc -b` y `eslint --max-warnings 0` sin hallazgos.
- Hallazgo real detectado pero fuera de alcance de este ticket (regla #10, mejora continua — no resuelto acá): `GenerationStep.vue` (comentario post-074) todavía tiene un badge etiquetado "Polígonos (cuboides)" en su código — contradice la regla del documento de definición de nunca llamar "polígonos" a los cuboides. No tocado (fuera del alcance explícito de este ticket, "no cambiar el layout general"), pero queda señalado para un ticket de UI polish futuro.
