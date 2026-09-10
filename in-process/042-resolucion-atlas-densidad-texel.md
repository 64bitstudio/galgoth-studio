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
