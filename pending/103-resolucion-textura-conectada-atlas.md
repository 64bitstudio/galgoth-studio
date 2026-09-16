# 103 — Resolución de textura conectada de verdad al atlas

## Objetivo
Nace de `docs/definiciones/anatomia-por-capas-generacion-mobs.md` (HU-10). La auditoría confirmó que el selector "Resolución de textura" en `ConfigurationStep.vue` existe visualmente pero nunca fue cableado — `confirm()` nunca incluye `textureResolution.value` en el payload. Es plomería faltante, no un refactor de arquitectura: `AtlasResolutionCalculator` ya calcula el atlas real a partir de una `TexelDensity` hardcodeada (`TexelDensity.X1`) en `GeometryPlannerService`.

**Depende de:** ninguno de los tickets anteriores de forma estricta (es un fix acotado), aunque conviene secuenciarlo después de 098-099 para evitar conflictos de merge en el mismo archivo (`GeometryPlannerService`).

## Alcance
**Incluye:**
- `ConfigurationStep.vue`: el valor de `textureResolution` viaja en el payload de `POST /api/mobs/{mobId}/generate`.
- Backend: el valor recibido se traduce a una `TexelDensity` real, reemplazando el hardcode `TexelDensity.X1` en el punto donde `GeometryPlannerService` llama a `AtlasResolutionCalculator.computeAtlas`.
- El atlas resultante respeta las reglas de potencia-de-2 ya existentes.

**No incluye:**
- Cambios al algoritmo de `AtlasResolutionCalculator` en sí — solo se conecta el parámetro que ya recibe.

## Criterios de aceptación (TDD)
- Dado que selecciono una resolución en Configuración, cuando genero, entonces ese valor viaja en el request.
- Dado el request con una resolución específica, cuando se calcula el atlas, entonces se usa como `TexelDensity` real (no el hardcode anterior).
- Dado el atlas resultante, cuando lo inspecciono, entonces su tamaño corresponde a la resolución elegida, dentro de las reglas de potencia-de-2 ya existentes.
- Test de integración frontend: cambiar el selector cambia el payload real enviado.

## Hecho
