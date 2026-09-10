# 044 — BBModelExporterV5/V4: export(model) sin UvLayoutStrategy + LegacyUvNormalizationService

**Milestone:** M7 · **Depende de:** 010, 014, 041 · **HUs:** HU-43 · **Épica:** O (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §3 — reversión directa de una decisión previa, explícita en el documento: el exportador nunca debe calcular UV). `BBModelExporterV4`/`V5` cambian de firma `export(model, uvLayoutStrategy)` a `export(model)`: nunca invocan ninguna estrategia de layout, nunca mutan el `UvLayout` recibido, serializan EXACTAMENTE `model.uv()`/`model.texture()`. Para revisiones legacy de Fase 1+2 (que dependían del recompute automático), se agrega `LegacyUvNormalizationService` como paso explícito y separado, invocado por `MobExportService` ANTES del exportador.

## Criterios de aceptación (TDD)
- Dado `BBModelExporterV5.export(model)` (nueva firma, un solo argumento), cuando exporta cualquier `MobProjectModel`, entonces NUNCA invoca `UvLayoutSelector`/`AlphaAutoPackStrategy`/`StableUvStrategy` — verificado con un mock/spy que falla el test si se invoca cualquiera de esas clases.
- Dado el mismo modelo, cuando se exporta dos veces sin cambios entre medio, entonces produce bytes idénticos (determinismo, sin dependencia de estado externo).
- Dado los tests/fixtures existentes de 010/011/012/013/014 (Blockbench real), cuando se actualizan a la nueva firma, entonces producen exactamente el mismo `.bbmodel` que antes del cambio — regresión cero, ningún fixture nuevo necesario para este caso.
- Dado `LegacyUvNormalizationService.normalizeIfSafe(model)`, cuando el modelo NO tiene ninguna región `PAINTED`/`ORPHAN` Y su `UvLayout` almacenado difiere estructuralmente de lo que `AlphaAutoPackStrategy` calcularía hoy para esa geometría, entonces devuelve un `MobProjectModel` con el `UvLayout` recalculado — transitorio, en memoria, NUNCA persistido de vuelta a la Revision.
- Dado un modelo con al menos una región `PAINTED`, cuando se invoca `normalizeIfSafe`, entonces es un no-op (devuelve el modelo sin tocar) — nunca normaliza sobre contenido pintado.
- Dado una fixture real de una revisión de Fase 1+2 (pre-existente, sin campo `status`), cuando se exporta con el pipeline completo (`normalizeIfSafe` → `export`), entonces el `.bbmodel` resultante es idéntico al que producía el exportador antes de este ticket — test de regresión explícito con esa fixture.
- Dado un modelo con textura real pintada, cuando se exporta, entonces la UV serializada es EXACTAMENTE la almacenada en la Revision (sin recompute) y el `.bbmodel` abre en Blockbench real sin diálogo de reparación.

## Hecho
