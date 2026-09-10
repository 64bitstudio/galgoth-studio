# 056 — Suite de aceptación E2E de Fase 3

**Milestone:** M10 · **Depende de:** 033, 043, 044, 048, 050, 054, 055 · **HUs:** HU-43 · **Épica:** O (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (HU-43, análoga a HU-23/ticket 033 de Fase 1+2). Automatiza el flujo E2E completo de Fase 3: partiendo de un mob con geometría ya usable (Fase 1+2), pintar/generar textura → verla en vivo en 3D → Guardar → exportar `.bbmodel` con la textura real → abre en Blockbench sin diálogos de reparación.

## Criterios de aceptación (TDD)
- Dado un mob con geometría ya usable (revisión ≥ 1, mismo punto de partida que HU-23), cuando pinto manualmente y/o genero por IA parte de su textura (`MockImageProvider`/`MockReasoningProvider`, 025/051 — sin necesidad de la API real de OpenAI/Anthropic) y hago clic en "Guardar", entonces se crea una nueva `mob_revision` cuyo `model_jsonb` refleja la textura actualizada, no el placeholder.
- Dado que exporto el mob, cuando genero el `.bbmodel`, entonces el archivo incluye la textura real — la textura placeholder checkerboard (011) queda reservada solo para mobs sin ninguna región pintada.
- Dado el `.bbmodel` exportado con textura real, cuando lo abro en Blockbench real, entonces abre sin diálogos de reparación (fixture nueva, mismo criterio que 009/012/014).
- Dado que valido el modelo, cuando corre la validación (HU-20), entonces sigue sin errores pendientes, ahora también con contenido de textura real.
- Dado que la suite corre en CI, cuando termina, entonces reporta verde — si aparece un bloqueo de infra análogo al de 033 (Docker-outside-of-Docker, CORS, etc.), se documenta explícitamente y se somete al PO la misma decisión que ya se tomó en 033 (aceptar el riesgo vs. seguir invirtiendo en el diagnóstico) — nunca se oculta un gap de CI en silencio.

## Hecho
