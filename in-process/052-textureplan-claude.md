# 052 — TexturePlan vía Claude (StructuredReasoningProvider)

**Milestone:** M9 · **Depende de:** 025, 028 · **HUs:** HU-36 · **Épica:** M (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §5, punto 1). Antes de generar ninguna imagen, se necesita un análisis de material/paleta y mapeo semántico por bone a partir de la imagen de referencia ya subida en Fase 2 — vía `StructuredReasoningProvider` (Claude, ya implementado en 025), NO el proveedor de imágenes. Mismo espíritu que `ModelIntent` (028): salida estrictamente validada por schema, el flujo se detiene si la validación falla.

## Criterios de aceptación (TDD)
- Dado una imagen de referencia ya subida en Fase 2, cuando se solicita un `TexturePlan`, entonces la respuesta incluye etiqueta semántica por bone, paleta dominante/acento, y notas de material por cara.
- Dado un `TexturePlan` devuelto por el proveedor, cuando se valida contra su JSON Schema (mismo patrón que `contracts/schemas/` para `ModelIntent`), entonces una respuesta inválida detiene el flujo SIN generar ninguna imagen — mismo criterio que 028.
- Dado que se completa la llamada, cuando se persiste, entonces `ai_jobs` guarda provider/modelo/versión de prompt/versión de esquema/IDs de referencia para este paso (mismo patrón ya usado para geometría en 028).
- Dado `MockReasoningProvider` (025), cuando se usa en tests de este ticket, entonces produce un `TexturePlan` determinista reproducible — sin llamar a Claude real.

## Hecho
