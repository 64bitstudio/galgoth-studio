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
