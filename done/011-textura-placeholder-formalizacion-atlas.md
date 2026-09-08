# 011 — Textura placeholder auto-generada + formalización del atlas

**Milestone:** M1 · **Depende de:** 010, 006 · **HUs:** HU-19, HU-20 · **Épica:** 009

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §7 y Addendum de implementación). Generar automáticamente una textura placeholder mínima (blanco/checkerboard) para mobs sin textura pintada, con dimensiones exactamente iguales a `textureWidth`/`textureHeight` del modelo — el mismo atlas que usa `AlphaAutoPackStrategy` (006) — para que el export pase los checks de "texture indexes exist" y abra sin diálogo de reparación.

## Criterios de aceptación (TDD)
- Dado un `MobProjectModel` sin textura pintada, cuando se exporta, entonces se genera e incluye una textura placeholder cuyas dimensiones en píxeles son exactamente `textureWidth` x `textureHeight` del modelo.
- Dado la UV generada por 006 para ese mismo modelo, cuando se compara contra las dimensiones de la textura placeholder, entonces todas las coordenadas UV caen dentro de esos bounds (sin overflow).
- Dado un modelo cuya geometría dispara `UV_ATLAS_OVERFLOW` en 006, cuando se intenta exportar, entonces el export falla con un error accionable (no genera silenciosamente una textura más grande).

## Hecho

- **`BBModelExporterV5.export(MobProjectModel, UvLayoutStrategy)`** (overload nuevo -- el `export(model)` de un solo argumento del ticket 010 queda intacto, cero cambios a sus 6 tests): antes de construir el documento, SIEMPRE invoca `uvLayoutStrategy.layout(...)` sobre los cuboids del modelo con las dimensiones reales de `MobProjectModel.texture`, y usa el resultado (cuboids con UV fresca) para el resto del export. Esto es una decisión de diseño deliberada, no solo "lo que pedía el ticket": `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §6, HU-16) ya establece que exportar es uno de los puntos donde el backend debe recomputar/revalidar la UV como autoridad canónica, igual que Guardar/Apply — nunca confiar en la UV que trae el modelo de entrada.
- **`PlaceholderTexture.generatePng(width, height)`**: checkerboard generado en memoria con `BufferedImage`+`ImageIO` (celdas de 8px, sin dependencias externas, sin necesidad de display real — seguro en Jenkins/contenedor headless). Se embebe como data URI base64 en `textures[0]` (`BBTexture`).
- **Investigación previa a escribir código**: se verificó contra el código fuente real de Blockbench (`js/outliner/abstract/face.ts`, `Face.getSaveCopy()`) que `Face.texture` (el índice que ya asigna `AlphaAutoPackStrategy` como `0`) se serializa como `Texture.all.indexOf(tex)` — es decir, referencia la **posición** de la textura dentro del array `textures[]`, no su campo `id`. Por eso alcanza con emitir una única textura en el índice 0: coincide exactamente con lo que AutoUv ya asignó.
- El overflow (AC #3) no necesitó un manejo especial: como `export(model, strategy)` invoca `uvLayoutStrategy.layout(...)` directamente, `UvAtlasOverflowException` (ya existente desde el ticket 006) se propaga tal cual — no se inventó un nuevo tipo de error ni se capturó/envolvió para "hacerlo más amigable", per la regla de "sin parches silenciosos".

**Tests**: 3 nuevos (`BBModelExporterV5PlaceholderTextureTest`), 100% en verde — 57 tests totales en el módulo backend (`./gradlew build -x sonar`), 0 fallos. El test de AC #1 decodifica el PNG embebido de verdad (no solo lee el campo `width`/`height` del JSON) para confirmar que la imagen real mide exactamente lo que dice.

**Fuera de alcance de este ticket:** verificación visual en Blockbench real de que el checkerboard se ve razonable sobre el modelo — la propia `docs/definiciones/galgoth-studio-mvp.md` (Riesgos y preguntas abiertas #7) marca esto como no bloqueante este ciclo y lo condiciona a Fase 3; el ticket 012 (fixtures reales) es donde se compara contra Blockbench de verdad.
