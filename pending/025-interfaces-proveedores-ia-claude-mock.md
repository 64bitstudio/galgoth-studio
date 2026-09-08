# 025 — Interfaces de proveedores IA + `ClaudeProvider` + `MockProvider`

**Milestone:** M4 · **Depende de:** 004 · **HUs:** HU-13

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §5). Implementar las interfaces `VisionModelProvider`/`StructuredReasoningProvider`/`ImageGenerationProvider`, con `ClaudeProvider` (default/activo) y `MockProvider` (uso exclusivo en tests) implementados este ciclo. `OpenAIProvider`/`RunPodProvider` quedan fuera, sin bloquear la abstracción.

## Criterios de aceptación (TDD)
- Dado el `VisionModelProvider` configurado como `ClaudeProvider`, cuando se invoca con una imagen de referencia, entonces retorna un `ModelIntent` crudo (sin validar todavía — eso es responsabilidad de 028).
- Dado el `MockProvider`, cuando se usa en tests, entonces retorna respuestas deterministas configurables sin llamar a ningún servicio externo.
- Dado el proveedor seleccionado por variable de entorno (`AI_VISION_PROVIDER`, `AI_REASONING_PROVIDER`), cuando se cambia de `ClaudeProvider` a `MockProvider`, entonces ningún código de `ai-orchestrator` ni del dominio necesita modificarse.
- Dado cada llamada a un proveedor, cuando se completa, entonces se registran proveedor, modelo, versión de prompt y versión de esquema (sección 20 del master prompt).
