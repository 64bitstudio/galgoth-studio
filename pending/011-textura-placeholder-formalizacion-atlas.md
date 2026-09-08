# 011 — Textura placeholder auto-generada + formalización del atlas

**Milestone:** M1 · **Depende de:** 010, 006 · **HUs:** HU-19, HU-20 · **Épica:** 009

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §7 y Addendum de implementación). Generar automáticamente una textura placeholder mínima (blanco/checkerboard) para mobs sin textura pintada, con dimensiones exactamente iguales a `textureWidth`/`textureHeight` del modelo — el mismo atlas que usa `AlphaAutoPackStrategy` (006) — para que el export pase los checks de "texture indexes exist" y abra sin diálogo de reparación.

## Criterios de aceptación (TDD)
- Dado un `MobProjectModel` sin textura pintada, cuando se exporta, entonces se genera e incluye una textura placeholder cuyas dimensiones en píxeles son exactamente `textureWidth` x `textureHeight` del modelo.
- Dado la UV generada por 006 para ese mismo modelo, cuando se compara contra las dimensiones de la textura placeholder, entonces todas las coordenadas UV caen dentro de esos bounds (sin overflow).
- Dado un modelo cuya geometría dispara `UV_ATLAS_OVERFLOW` en 006, cuando se intenta exportar, entonces el export falla con un error accionable (no genera silenciosamente una textura más grande).
