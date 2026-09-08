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

## Hecho

Implementado en `backend/src/main/java/com/galgothstudio/backend/domain/uv/`:

- **`UvLayoutStrategy`**: interfaz con un único método `layout(cuboids, textureWidth, textureHeight) -> Result(cuboids, regions)`. `GeometryEngine` depende solo de esta interfaz (inyectada como parámetro), nunca de `AlphaAutoPackStrategy` directamente — verificado con un test que pasa una implementación de prueba (passthrough) y confirma que el motor la usa en vez de la real (AC #3).
- **`AlphaAutoPackStrategy`**: desenvolvimiento de caja estándar de Minecraft por cuboid (footprint `2*(x+z)` de ancho × `(z+y)` de alto, con `up`/`down` en la fila superior y `west`/`north`/`east`/`south` en la inferior) + shelf-packing determinista de todos los footprints del modelo, fila por fila, en el mismo orden que la lista de cuboids de entrada. **La fórmula del box-UV-unwrap no estaba documentada en `galgoth_studio_build_pack/`** (se verificó primero: `TECHNICAL_REFERENCES.md` no la menciona, y el `.bbmodel` de muestra solo trae UV placeholder) — se derivó y verificó leyendo el código fuente real de Blockbench (`JannisX11/blockbench`, `js/outliner/types/cube.js` + `js/uv/uv.js`, específicamente el layout CSS de la vista previa de Box UV en el editor), no inventada ni asumida de memoria.
- **`UvAtlasOverflowException`** (`UV_ATLAS_OVERFLOW`): `{currentWidth, currentHeight, requiredWidth, requiredHeight}`. `requiredWidth`/`requiredHeight` se calculan re-simulando el mismo packing determinista sin límite de alto, con un ancho igual al mayor entre el ancho actual del atlas y el footprint individual más ancho — nunca inventa un número, es el resultado real de una segunda pasada del mismo algoritmo.
- **Integración con `GeometryEngine` (005)**: nuevo overload `apply(model, operaciones, uvLayoutStrategy)` — ejecuta la lógica atómica existente del motor (sin tocarla) y, solo si el batch incluyó `createCuboid`/`resizeCuboid`, invoca la estrategia sobre el modelo resultante y reemplaza `faces`/`uv.regions`. El overload de 2 argumentos del ticket 005 queda intacto (sin AutoUv) para quien no lo necesite — cero cambios a los 21 tests de ticket 005.
- **Límites del atlas**: se toman de `MobProjectModel.texture.width/height` (fuente única de verdad, tal como pide el Objetivo del ticket), no de `uv.textureWidth/textureHeight` — el resultado siempre re-sincroniza `uv.textureWidth/textureHeight` con `texture.width/height` para que nunca queden desincronizados. Se agregó un test específico (`losLimitesDelAtlasSeTomanDeTextureNoDeUvBookkeepingPotencialmenteDesincronizado`) que habría fallado con la primera versión de esta implementación (usaba `uv.textureWidth/Height` por error) — hallazgo propio, corregido antes de abrir el PR, no en un round de CI.
- `Face.texture` se fija en `0` (único atlas/textura que existe este ciclo) cuando AutoUv corre — antes quedaba `null` (placeholder puro del Geometry Engine sin AutoUv).

**Tests**: 10 nuevos (`AlphaAutoPackStrategyTest`, 5; `GeometryEngineUvIntegrationTest`, 5), 100% en verde — 48 tests totales del módulo backend tras este ticket (`./gradlew build -x sonar`), 0 fallos.

**Fuera de alcance de este ticket (según su propio Objetivo, es explícito "Fase 3" en el doc de definición):** `StableUvStrategy` (preservar UV sobre textura ya pintada) no se implementa — solo se dejó la interfaz correcta para no romper compatibilidad después. Tampoco se implementa mirroring de UV (`mirror_uv` de Blockbench, para partes simétricas como brazos izquierdo/derecho) — no está en las AC de este ticket ni en HU-16.
