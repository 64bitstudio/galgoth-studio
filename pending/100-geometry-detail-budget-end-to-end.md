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
