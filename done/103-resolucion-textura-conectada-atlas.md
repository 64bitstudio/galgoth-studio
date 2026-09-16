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
### Conflicto encontrado y resuelto con decisión del PO (antes de escribir código)
El ticket asumía que era plomería: "traducir el valor recibido a una `TexelDensity` real". Al abrir el código apareció un conflicto que no estaba en el ticket: el selector ofrece **tamaños absolutos** (64×64/128×128/256×256), pero el Diseño técnico §7 de `galgoth-studio-fase3-textura.md` (cerrado por el PO el 10-sep-2026) dice que el atlas es una **consecuencia** del packing a una densidad dada, "nunca un valor fijo elegido de antemano". Además solo existen dos densidades (X1/X2) para tres opciones de UI, así que un mapeo 1:1 tampoco era posible. Se consultó al PO con las alternativas reales y **decidió: el tamaño elegido es un TOPE**, no un tamaño exacto.

### Implementado
- `TextureResolution` (nuevo enum de dominio, wire `"64"`/`"128"`/`"256"`): `highestDensityWithin(...)` elige la MAYOR `TexelDensity` cuyo atlas resultante entra en el tope. Si ni la más baja entra, devuelve la más baja igual y se registra advertencia — el tope nunca tumba la generación (mismo criterio que el presupuesto de `GeometryDetail`).
- `GeometryPlannerService.withInitialAtlas` ya no usa el hardcode `TexelDensity.X1`: la densidad sale del tope recibido. Nueva sobrecarga `applyOperations(..., TextureResolution)`; la de 3 args queda por compatibilidad con el default.
- Plomería completa: `StartGenerationRequest` (campo aditivo) → `GenerationJobController` (default `MAX_128`) → `MobGenerationService.startGeneration` (nueva sobrecarga de 3 args, las anteriores delegan) → `GenerationJobContext` → `applyOperations`.
- Frontend: `TextureResolution` en `generationApi.ts`, el `value` de las opciones pasa a ser el valor de contrato (**las etiquetas visibles NO cambian**: siguen diciendo 64×64/128×128/256×256), `ConfigurationStep` lo emite en `confirm`, `AiMobWizard` lo retiene y `GenerationStep` lo manda en el `POST /generate`.
- `docs/API.md` documenta el campo nuevo y, explícitamente, que es un tope y no un tamaño exacto.

### Cambio de comportamiento real, señalado explícitamente (regla 9)
Hasta este ticket la densidad era siempre X1. Ahora, con el tope por defecto (128), la densidad X2 entra para la mayoría de los mobs, así que **el flujo por defecto pasa a generar atlas del doble de lado que antes** (más detalle de textura). No es un efecto colateral oculto: es la consecuencia directa de la semántica de tope que eligió el PO. Lo detectó un test existente (`elAtlasInicial_seCalculaDelFootprintEmpaquetadoRealDeLosCuboids_nuncaDeUnValorFijo`, que pasó de esperar 32×16 a 64×32); se actualizó documentando el porqué en su Javadoc, **no se tocó para "que pasara"**.

Consecuencia también aceptada y documentada: en mobs chicos, dos topes distintos pueden dar el mismo atlas (si a X2 ya entra en el tope menor). El tope nunca infla un atlas para llenarlo — eso sería justo el "valor fijo elegido de antemano" que §7 prohíbe.

### Tests
- `TextureResolutionTest` (4, nuevo): elige la mayor densidad que entra, un tope mayor no infla el atlas, nunca falla si ni la más baja entra, y cada opción expone su tope.
- `GeometryPlannerServiceTest` (+1): el MISMO cuboid con tope 64 vs 128 produce atlas reales distintos (64×32 vs 128×64) — la prueba de que la resolución ya no es cosmética.
- `ConfigurationStep.spec.ts` (+1): elegir 256×256 cambia el `textureResolution` del `confirm` emitido (antes el selector no viajaba en el payload).
- Backend 565/565 en verde. Frontend 913/913, `vue-tsc` limpio.

### No aplica
Sin cambios de etiquetas visibles ni de layout (solo cambia el `value` interno de las opciones), así que no corresponde el flujo de preview visual. Sin endpoints nuevos: no hay colección Postman que actualizar (el endpoint ya existía; solo ganó un campo opcional de body, documentado en `docs/API.md`).
