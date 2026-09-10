# 051 — OpenAiImageProvider: modelo configurable

**Milestone:** M9 · **Depende de:** 025 · **HUs:** HU-39, HU-40 · **Épica:** M (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §5/§12, valor default corregido en la ronda de VoBo final del 10 sep 2026). Implementación real de `ImageGenerationProvider` (interfaz ya definida en 025, hasta ahora sin proveedor real) vía OpenAI, con el modelo de imagen **configurable** (nunca hardcodeado en dominio/lógica de negocio). `MockImageProvider` pasa de placeholder de interfaz a un doble determinista real y utilizable en tests.

## Criterios de aceptación (TDD)
- Dado `ImageGenerationProvider.generateTextureSheet(TextureGenerationSheetRequest)` (extensión aditiva sobre la interfaz de 025), cuando se agrega, entonces `generateImage(String)` permanece sin cambios — los tests existentes de 025 siguen en verde sin modificarlos.
- Dado `ai.openai.image-model=${OPENAI_IMAGE_MODEL:gpt-image-2.5-sunburst-2026-09-08}` (snapshot fechado, preferido sobre un alias flotante por reproducibilidad), cuando `OpenAiImageProvider` arma una llamada, entonces usa el valor configurado — nunca un literal de modelo en el cuerpo de la petición ni en ninguna clase de dominio.
- Dado que existe una región/contenido a preservar (inpaint), cuando se genera, entonces se usa `/v1/images/edits` con máscara/imagen base; sin contenido previo, entonces se usa `/v1/images/generations` — verificar contra la documentación real de OpenAI vigente al implementar (ambos endpoints/capacidades pueden requerir ajuste).
- Dado que se dispara cualquier llamada de generación, cuando se registra el job (`ai_jobs`), entonces `ai_jobs.model` persiste EXACTAMENTE el valor configurado en `ai.openai.image-model` en ESE momento — test que cambia la configuración entre dos llamadas y confirma que cada `ai_jobs` guardó el valor real usado en su momento, no el más reciente.
- Dado `MockImageProvider`, cuando se usa en tests, entonces es un doble determinista real (produce bytes de imagen reproducibles/verificables), no solo una interfaz sin comportamiento — mismo patrón que `MockProvider`/`MockReasoningProvider` de 025.

## Hecho
