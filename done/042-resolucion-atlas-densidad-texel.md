# 042 — Resolución del atlas: densidad de texel (x1/x2)

**Milestone:** M7 · **Depende de:** 041 · **HUs:** HU-29 · **Épica:** J (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §7, cerrado definitivamente por el PO el 10 sep 2026). `x1`/`x2` son perfiles de **densidad de texel** (1 o 2 texels por unidad de modelo Minecraft/Blockbench), no una dimensión de atlas — `AutoUv` calcula el footprint de cada cara a la densidad elegida y el tamaño del atlas resulta del packing, nunca de un valor elegido de antemano. Antes de que exista contenido `PAINTED`, la densidad y el atlas son ajustables; después, quedan congelados.

## Criterios de aceptación (TDD)
- Dado un cuboid con una cara física de 8×8 unidades, cuando `BoxUvMath.footprintOf(cuboid, texelDensity)` calcula su footprint a densidad `x1`, entonces produce 8×8 texels; a densidad `x2`, entonces produce 16×16 texels.
- Dado un `BaseType` Minecraft (`HUMANOID`/`ARACHNID`/`QUADRUPED`/`FLYING`), cuando se calcula el atlas inicial a densidad `x1` (default), entonces `AutoUv` empaqueta los footprints a esa densidad y el atlas resultante es la potencia de 2 que los contiene — nunca un valor fijo predefinido.
- Dado el mismo mob, cuando el usuario dispara el upgrade explícito a `x2` en el paso de Configuración (antes de pintar), entonces todos los footprints se recalculan al doble de densidad y se reempaquetan — el atlas resultante cambia de tamaño en consecuencia (test que compara los dos tamaños de atlas para la misma geometría).
- Dado un `BaseType.CUSTOM`, cuando se calcula el atlas inicial, entonces el footprint se calcula a densidad estándar (`x1`) y el atlas es la potencia de 2 inmediatamente superior al footprint empaquetado.
- Dado que el footprint empaquetado a la densidad vigente no cabe en la resolución actual, cuando esto ocurre ANTES de pintar, entonces se permite un upgrade explícito (recalcular a `x2`, o la siguiente potencia de 2 para custom) — nunca un crecimiento silencioso.
- Dado que existe al menos una región `PAINTED`, cuando se intenta cambiar la densidad de texel o `width`/`height` del atlas, entonces la operación no tiene efecto — ambos quedan congelados (test que confirma que un intento de upgrade post-pintado es rechazado o ignorado explícitamente, nunca aplicado).
- Dado que una región nueva no cabe en el espacio libre del atlas ya congelado, cuando esto ocurre, entonces se lanza `UvAtlasOverflowException` (reutilizando la excepción de 041) — el atlas nunca crece ni recalcula densidad para hacerle espacio.
- Dado el campo "Resolución de textura" del wizard IA (ticket 027), cuando se implementa este ticket, entonces deja de enviarse como parámetro vinculante al backend — a lo sumo dispara el upgrade explícito de densidad descrito arriba.

## Hecho

`TexelDensity` (enum `X1(1)`/`X2(2)`) + `AtlasResolutionCalculator` (footprint empaquetado a la densidad elegida vía el shelf-packing ya verificado de `AlphaAutoPackStrategy`, potencia de 2 inmediatamente contenedora — nunca un valor fijo) + `TexelDensityUpgrade` (upgrade explícito x1→x2 pre-pintado, no-op explícito si ya existe `PAINTED`, sin excepción ni cambio silencioso).

**`BoxUvMath`/`AlphaAutoPackStrategy`**: nuevas sobrecargas aditivas con `TexelDensity` — las viejas (sin densidad) delegan en `X1`, cero cambio de comportamiento para 006/007/041. `AlphaAutoPackStrategy.packedBoundsOf` (paquete-visible) reutiliza el mismo shelf-packing para calcular la caja envolvente sin placements, evitando duplicar el algoritmo.

**Hallazgo real corregido a mitad de camino**: el primer diseño calculaba el atlas a X2 pero seguía empaquetando las caras reales a X1 — un "upgrade" falso (atlas más grande, mismo detalle de siempre). Corregido extendiendo `BoxUvMath`/`AlphaAutoPackStrategy` para que el upgrade también reempaquete con densidad real (cara de 8×8 unidades → 16×16 texels a X2, verificado con test).

**`GeometryPlannerService.applyOperations`**: dejó de confiar en el `TextureDocument` fijo (128×128 hardcodeado) de `startingModel` — aplica la geometría en dos pasadas (sin UV para conocer los cuboids reales que la IA propuso, luego con el atlas correcto ya calculado a densidad X1) antes de invocar la estrategia real de UV.

**Frontend**: sin cambios de código — se confirmó leyendo `ConfigurationStep.vue`/su test que el campo "Resolución de textura" YA NO se envía como parámetro vinculante (satisfecho antes de este ticket). Inconsistencia señalada, no corregida (fuera de alcance): el `<select>` sigue mostrando dimensiones (64×64/128×128/256×256) en vez de la semántica x1/x2 — pendiente del ticket que cablee el upgrade explícito a la UI.

**Gap documentado, no resuelto (fuera de alcance explícito del ticket)**: ni `MobProjectModel` ni `UvLayout` persisten qué densidad está vigente — el upgrade a X2 es una foto única; cualquier operación de geometría posterior que pase por el camino estándar (`UvLayoutSelector`/`AlphaAutoPackStrategy` sin densidad) recalculará a X1 implícito. Coherente con lo que el ticket pidió (no tocar `StableUvStrategy`/la interfaz `UvLayoutStrategy` del contrato), pero un ticket futuro necesita agregar el campo de densidad persistido y cablearlo a través del pipeline completo antes de exponer el upgrade en el wizard.

**Coordinación real de esta sesión, señalada explícitamente**: el agente que implementó este ticket detectó un commit ajeno (`fix(041)`, hallazgo de Sonar de la rama 041) mezclado en su propia rama por compartir working directory sin aislamiento — no lo tocó, lo reportó. Se reconcilió después: el commit se movió a la rama real de 041 (cherry-pick + push) y se limpió la rama de 042 antes de continuar. A partir de este ticket, los agentes de implementación se lanzan con `isolation: "worktree"` para que esto no se repita.

**Tests**: 285 tests de backend (+16 desde 041: `BoxUvMathTest` +7, `AtlasResolutionCalculatorTest` 6, `TexelDensityUpgradeTest` 4, `GeometryPlannerServiceTest` +1), 0 failures.
