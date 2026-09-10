# 053 — TextureGenerationSheetPlanner + TextureSheetSlicer + TextureCompositorService: aislamiento espacial

**Milestone:** M9 · **Depende de:** 041, 051, 052 · **HUs:** HU-37, HU-40 · **Épica:** M (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §11 y §21 — DoD de aislamiento espacial agregado explícitamente por el PO en el VoBo final, 10 sep 2026). Reemplaza el enfoque de "una llamada por cuboid" por **una sola imagen coherente por bone**: `TextureGenerationSheetPlanner` arma el `TextureGenerationSheet` (todas las caras/cuboids del bone, sin solapes), `TextureSheetSlicer` recorta cada cara de la imagen generada, `TextureCompositorService` la compone en el atlas real. La IA propone contenido visual; estos tres componentes son la ÚNICA autoridad espacial.

## Criterios de aceptación (TDD)
- Dado un bone con varios cuboids, cuando `TextureGenerationSheetPlanner` arma el `TextureGenerationSheet`, entonces los `sheetRect` de todos sus `CuboidFacePlacement` NUNCA se solapan entre sí — incluye gutters/padding explícitos entre caras adyacentes (constante de diseño fijada en este ticket, documentada en el código).
- Dado el mismo `TextureGenerationSheet`, cuando se compone el prompt para `ImageGenerationProvider.generateTextureSheet(...)`, entonces incluye un background/mask determinista que delimita visualmente cada `sheetRect`.
- Dado que `TextureSheetSlicer` recorta un slice para un `CuboidFacePlacement`, cuando lo hace, entonces **solo lee píxeles dentro del `sheetRect` asignado** — nunca un píxel fuera de ese rect, verificado con un test que usa una imagen generada con contenido deliberadamente "sangrado" (bleed) fuera del rect esperado.
- Dado ese mismo caso de bleed, cuando el slicer procesa la imagen, entonces el contenido fuera del rect se descarta silenciosamente — nunca se compone en el atlas, nunca dispara un error visible al usuario.
- Dado `TextureCompositorService` componiendo dos slices adyacentes del mismo bone, cuando ambos se escriben, entonces **ningún píxel de un placement modifica la región del otro** — test explícito que verifica los píxeles de un `atlasUvRect` vecino permanecen sin cambios tras componer el slice contiguo.
- Dado un slice cuyas dimensiones no calzan exactamente con su `atlasUvRect` esperado, cuando `TextureCompositorService` lo recibe, entonces lo recorta/escala automáticamente antes de componerlo — sin reintentar la generación ni mostrar error al usuario.
- Dado que el propio recorte/decodificación de un slice falla técnicamente, cuando esto ocurre, entonces se lanza `TextureGenerationFailedException` y NO se aplica nada — nunca se compone un atlas parcialmente corrupto.
- **Fallback/batching**: dado que `sheetWidth`×`sheetHeight` de un bone excede los límites técnicos del modelo de imagen configurado, cuando esto ocurre, entonces `TextureGenerationSheetPlanner` divide el bone en N sub-sheets (bin-packing determinista) y dispara N llamadas, cada una visible explícitamente en el progreso (`stage=generando_bone_X (parte N/M)`) — nunca un camino silencioso indistinguible de una sola llamada.

## Hecho
