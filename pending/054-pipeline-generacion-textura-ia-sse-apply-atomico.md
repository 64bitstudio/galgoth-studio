# 054 — Pipeline completo de generación de textura por IA: SSE + diff + Apply atómico + 409

**Milestone:** M9 · **Depende de:** 045, 051, 052, 053 · **HUs:** HU-36, HU-37, HU-38, HU-39 · **Épica:** M (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §6/§10/§13/§16). Orquesta el pipeline end-to-end de generación/regeneración de textura por IA: `TexturePlan` → `TextureGenerationSheet` por bone → generación → slicing/composición → progreso SSE con el esquema formal de `preview_texture_patch` → diff Antes/Después → Apply (atómico, con conflicto 409) / Reject.

## Criterios de aceptación (TDD)
- Dado que el usuario elige estilo (1 de 4: Fiel a la referencia / Minecraft Vanilla / Pixel Art / Realista), nivel de detalle, y opcionalmente un `boneId`, cuando confirma la generación, entonces se dispara el pipeline completo reutilizando la imagen de referencia ya subida en Fase 2 — nunca pide una nueva.
- Dado el pipeline corriendo, cuando reporta progreso, entonces reutiliza `ai_job_events`/SSE existente con los nuevos valores de `stage` (`analizando_paleta`, `mapeando_caras`, `generando_bone_X`, `componiendo_atlas`, `limpiando_pixeles`).
- Dado un evento `preview_texture_patch`, cuando se emite, entonces cumple el esquema `{ type, rect, encoding: 'base64'|'asset_url', data|url }` — payloads de hasta 32 KB en base64 van inline; por encima, como asset temporal en MinIO. Estos previews NUNCA se persisten como textura definitiva (test que confirma que ningún preview toca `mob_drafts`/`mob_revisions`/`textures/{hash}.png` fuera de un Apply real).
- Dado que el pipeline termina, cuando se presenta el resultado, entonces se muestra como una PROPUESTA con diff Antes/Después (región afectada, o atlas completo si fue "Modelo completo") — con Apply/Reject explícitos.
- Dado Reject, cuando se ejecuta, entonces no modifica ni el draft ni ninguna revisión.
- Dado Apply, cuando se invoca, entonces primero verifica conflicto: si CUALQUIER parte del draft/revisión compartido (geometría O textura) avanzó desde que se generó la propuesta, responde 409 y descarta la propuesta sin tocar nada.
- Dado Apply SIN conflicto, cuando se ejecuta, entonces actualiza de forma ATÓMICA: (1) sube el bitmap a MinIO PRIMERO, fuera de la transacción de base de datos; (2) UNA transacción Postgres que actualiza `mob_drafts`, inserta `mob_revisions` (texture+geometría juntas) y actualiza `current_revision_number` — todo o nada (test de integración que fuerza un fallo a mitad de la transacción y confirma que NINGUNA fila queda escrita a medias).
- Dado un Apply exitoso, cuando se hace un `GET` posterior del draft/modelo, entonces se obtiene EXACTAMENTE la textura aplicada — nunca un estado intermedio.
- Dado la migración `V3__ai_jobs_texture_job_types.sql`, cuando se aplica, entonces `ai_jobs` acepta `job_type` `generate_texture`/`edit_texture` y persiste `target_bone_id` (nullable) para regeneraciones de un bone específico.
- Dado que un bone regenerado tenía contenido pintado a mano, cuando se genera el diff, entonces señala explícitamente que se sobrescribirá contenido pintado a mano — no solo contenido generado previamente por IA.

## Hecho
