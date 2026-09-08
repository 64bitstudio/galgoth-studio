# 006 — AutoUv backend: `UvLayoutStrategy` + `AlphaAutoPackStrategy`

**Milestone:** M0 · **Depende de:** 005 · **HUs:** HU-16

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §6 y Addendum de implementación). Implementar la asignación automática de UV en backend como autoridad canónica, invocada por el Geometry Engine (005) en cada `createCuboid`/`resizeCuboid`. Se introduce la interfaz `UvLayoutStrategy` con una única implementación este ciclo, `AlphaAutoPackStrategy` (shelf-packing determinista tipo caja de Minecraft) — sin acoplar `AutoUv`/`MobProjectModel` a la suposición de que siempre se puede re-empaquetar toda la UV libremente (Fase 3 necesitará una `StableUvStrategy` que preserve UV sobre textura pintada; no se implementa todavía, solo se deja la abstracción).

**Formalización del atlas (obligatoria):** las dimensiones del placeholder deben ser exactamente `textureWidth`/`textureHeight` del modelo (`MobProjectModel.texture`); toda UV generada debe quedar dentro de esos bounds. Si `AlphaAutoPackStrategy` no puede acomodar el modelo en el atlas actual, retorna un error de dominio `UV_ATLAS_OVERFLOW` con las dimensiones actuales y las requeridas — **nunca** aumenta la resolución del atlas en silencio.

## Criterios de aceptación (TDD)
- Dado un `createCuboid`/`resizeCuboid` aplicado vía Geometry Engine, cuando se ejecuta, entonces `AlphaAutoPackStrategy` asigna UV válida en las 6 caras, dentro de `[0, textureWidth) x [0, textureHeight)`.
- Dado un modelo cuya suma de footprints de cuboides excede el atlas configurado, cuando se intenta empaquetar, entonces se lanza `UV_ATLAS_OVERFLOW` con `{currentWidth, currentHeight, requiredWidth, requiredHeight}` — el atlas **no** crece automáticamente.
- Dado la interfaz `UvLayoutStrategy`, cuando se inspecciona el código de `AutoUv`, entonces `AlphaAutoPackStrategy` es intercambiable sin tocar el Geometry Engine ni `MobProjectModel` (mismo patrón que los providers de IA de 025).
- Dado el mismo `MobProjectModel` de entrada, cuando se ejecuta `AlphaAutoPackStrategy` dos veces, entonces produce exactamente el mismo layout (determinismo).
