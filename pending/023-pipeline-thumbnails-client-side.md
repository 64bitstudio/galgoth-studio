# 023 — Pipeline de thumbnails client-side

**Milestone:** M3 · **Depende de:** 016, 020, 022 · **HUs:** HU-02, HU-04

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §8). Generar miniaturas automáticamente en cada commit relevante (Guardar/Apply/Usar este modelo — este ticket cubre el disparo desde Guardar vía 020; Apply/Usar este modelo se conectan en 030/031), renderizando offscreen con el viewport ya montado (016) y subiendo el PNG como asset (`mobs.thumbnail_key`). Sin WebGL activo por card en los grids (021/022).

## Criterios de aceptación (TDD)
- Dado un commit relevante (Guardar, vía 020), cuando se confirma, entonces se dispara un render offscreen y se sube el PNG resultante, actualizando `mobs.thumbnail_key`.
- Dado un grid de proyectos/mobs, cuando se renderiza, entonces muestra `<img>` desde `thumbnail_key` — cero contextos WebGL en la pantalla de listado.
- Dado que la generación o subida del thumbnail falla, cuando ocurre, entonces el commit que la disparó **no se revierte**, se conserva el thumbnail anterior (o un placeholder genérico), y queda disponible para reintento posterior.
- Dado un mob sin thumbnail generado todavía, cuando se muestra en el grid, entonces se ve un placeholder genérico sin bloquear el listado.
