# 109 — Densidad de téxel X4 + el selector de textura elige densidad (sin tope de atlas)

## Objetivo
Nace de `docs/definiciones/densidad-de-texel-y-resolucion-de-textura.md` (HU-1, HU-2, HU-3), con VoBo del PO. Hoy la UV asigna téxeles proporcionales al tamaño físico: en `Carcomido v2`, 178 de 276 caras quedan bajo 16 px² y los rasgos que dan identidad (ojos 2×1.2 u, colmillos 1×1 u) reciben 1-2 téxeles, así que la IA solo puede devolver ruido. Este ticket sube la densidad a `X4`, deja el atlas libre (sin tope) y convierte el selector de la UI en lo único que de verdad puede elegir el usuario: la densidad.

**Depende de:** 103 (revierte su semántica de tope). **Bloquea:** 110 (la métrica mide el efecto de este cambio).

## Alcance
**Incluye:**
- `TexelDensity.X4` (4 téxeles por unidad de modelo).
- Nuevo enum de dominio `TextureDensity` (STANDARD→X1, HIGH→X2, MAX→X4) que reemplaza a `TextureResolution`; **default MAX**.
- `GeometryPlannerService` usa la densidad elegida directamente: se elimina `highestDensityWithin`, el tope y su advertencia. El atlas vuelve a ser consecuencia pura del packing (Diseño técnico §7).
- Plomería completa del campo renombrado en el request, el contexto del job y el planner.
- Frontend: el selector pasa a "Estándar / Alta (recomendado) / Máxima" mapeado a la densidad real, con el valor viajando en el `POST /generate`.
- `docs/API.md`: documentar el cambio de contrato.

**No incluye:**
- Color plano para caras chicas ni densidad por categoría semántica (descartado por el PO en la definición).
- Cambios a la política de geometría secundaria.
- Migrar mobs ya generados (conservan su atlas; la densidad nueva aplica a generaciones nuevas).

## Criterios de aceptación (TDD)
- Dado un cuboid de 1×1 unidades, cuando se calcula su UV a `X4`, entonces su cara recibe al menos 4×4 téxeles.
- Dado el mismo modelo, cuando se genera con STANDARD vs MAX, entonces el atlas resultante difiere de forma observable (la elección tiene efecto real, AC de HU-3).
- Dado cualquier modelo, cuando se calcula el atlas, entonces no existe ningún tope que degrade la densidad elegida — el atlas es la potencia de 2 que contiene el packing.
- Dado un request sin `textureDensity`, cuando se genera, entonces se usa MAX (default) y el contrato anterior sin body sigue funcionando.
- Suite backend + frontend en verde; `TextureResolution` y su test quedan eliminados, no huérfanos.

## Hecho
### Hallazgo que cambió el alcance del ticket (encontrado por un test, no por lectura)
El ticket asumía que bastaba con elegir una densidad más alta. Al escribir el AC de "un cuboid de 1×1 recibe al menos 4×4 téxeles", **el test falló con caras de 1×1 téxel incluso a X4**. Causa: la densidad solo llegaba a `AtlasResolutionCalculator` (el TAMAÑO del atlas), nunca al packing real — `GeometryEngine.apply` invocaba `UvLayoutStrategy.layout(...)` por sobrecargas sin densidad y `AlphaAutoPackStrategy` usaba `TexelDensity.X1` fijo internamente.

**Consecuencia para atrás**: el ticket 103 era cosmético en el peor sentido. Su "más densidad" agrandaba el lienzo y dejaba las caras del mismo tamaño — más espacio vacío, cero mejora de detalle. Cualquier control de resolución expuesto al usuario era necesariamente inerte. Nadie lo habría notado sin este test, porque el atlas efectivamente cambiaba de tamaño.

Corregido conectando la densidad de punta a punta:
- `UvLayoutStrategy`: sobrecarga aditiva de 6 args con `TexelDensity`; default delega en la de 5 args (cero cambio para implementaciones que no la soporten, p. ej. `StableUvStrategy`).
- `AlphaAutoPackStrategy`: la implementa de verdad, delegando en su `layout(..., density)` que ya existía desde el 042 pero solo usaba `TexelDensityUpgrade`.
- `GeometryEngine.apply`: sobrecarga con densidad; las anteriores delegan con `X1` — exactamente lo que hacían de hecho.
- `GeometryPlannerService`: pasa la densidad elegida tanto al cálculo del atlas como al packing.

### Implementado
- `TexelDensity.X4` (4 téxeles por unidad). A X2 los colmillos quedaban en 2×2: insuficiente, por eso el salto.
- `TextureDensity` (STANDARD→X1, HIGH→X2, MAX→X4, **default MAX**) reemplaza a `TextureResolution`, que se eliminó junto con su test — sin huérfanos.
- Sin tope: el atlas vuelve a ser la potencia de 2 que contiene el packing (§7). Se eliminó `highestDensityWithin`, la advertencia de tope y el logger que quedó sin uso.
- Frontend: el selector pasa a "Estándar / Alta / Máxima (recomendada)" con etiqueta "Densidad de textura". Las etiquetas 64/128/256 se eliminaron porque prometían un tamaño de atlas que el motor ya no garantiza.
- `docs/API.md`: documentado el cambio de contrato.

### Cambio de contrato (regla 9)
`textureResolution` **deja de existir** en `POST /api/mobs/{mobId}/generate`; lo reemplaza `textureDensity` con valores `standard|high|max`. No es aditivo. Se acepta porque el campo anterior nació hace horas (ticket 103), su único consumidor es este frontend, y describía una semántica que el PO ya revirtió. Un request sin body sigue funcionando igual (default `max`).

### Tests
- `GeometryPlannerServiceTest`: el AC del cuboid de 1×1 con ≥4×4 téxeles (el que destapó el bug), y el de densidades distintas produciendo atlas distintos (64×32 a STANDARD vs 256×128 a MAX).
- El test del atlas inicial cambió de expectativa por segunda vez (32×16 → 64×32 → 128×64); documentado en su Javadoc por qué cada vez, sin tocarlo "para que pase".
- Backend 577/577. Frontend 913/913, typecheck y lint limpios.

### Pendiente de verificación en vivo
Que a X4 la IA produzca contenido estructurado y no ruido de mayor resolución sigue sin comprobarse: es el ticket 111, después del deploy. La hipótesis es que el cuello de botella era el destino y no la fuente, pero eso se afirma recién cuando se vea.
