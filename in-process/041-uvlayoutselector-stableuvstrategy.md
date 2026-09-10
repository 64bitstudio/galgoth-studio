# 041 — UvLayoutSelector + StableUvStrategy + BoxUvMath

**Milestone:** M7 · **Depende de:** 006, 007, 040 · **HUs:** HU-33, HU-34, HU-35 · **Épica:** L (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §2, VoBo final del PO). Implementa el contrato que resuelve el riesgo abierto #7 de Fase 1+2: una vez que existe textura pintada, la UV ya no puede reempaquetarse libremente. `UvLayoutSelector` (nuevo, `@Primary`) decide entre `AlphaAutoPackStrategy` (Fase 1+2, sin cambios) y `StableUvStrategy` (nueva) según si el `UvLayout` previo tiene alguna región `PAINTED`/`ORPHAN`. Se inyecta ÚNICAMENTE en `GeometryEngine.apply`, `GeometryPlannerService` y `AiGeometryEditPlannerService` — el exportador queda deliberadamente fuera (ticket 044).

## Criterios de aceptación (TDD)
- Dado un `UvLayoutStrategy.layout(cuboids, w, h, previousLayout)` (sobrecarga `default` nueva), cuando `AlphaAutoPackStrategy` no la sobreescribe, entonces delega en la sobrecarga de 3 argumentos existente — cero cambios de comportamiento, los tests existentes de `AlphaAutoPackStrategy` (006/007) siguen en verde sin modificarlos.
- Dado un `previousLayout` sin ningún `PAINTED`/`ORPHAN`, cuando `UvLayoutSelector.layout(...)` se invoca, entonces delega en `AlphaAutoPackStrategy` con resultado byte-idéntico al de Fase 1+2.
- Dado un `previousLayout` con al menos un `PAINTED`/`ORPHAN`, cuando se invoca, entonces delega en `StableUvStrategy`.
- **Resize de cara `PAINTED`**: dado un cuboid con una cara `PAINTED` cuyo footprint cambiaría por el resize, cuando `StableUvStrategy` procesa la operación, entonces lanza `PaintedRegionResizeConfirmationRequiredException` con el detalle de las caras afectadas — sin mutar nada.
- **Resize confirmado**: dado el mismo caso con `confirmPaintLoss=true`, cuando se aplica, entonces se crea una `UvReservation(rect viejo, RESIZE_ABANDONED)`, la cara se reempaqueta en espacio verdaderamente libre, y su `UvRegion` queda `UNPAINTED` en la nueva ubicación.
- **"Espacio verdaderamente libre"**: dado un atlas con regiones en cualquier estado Y reservas existentes, cuando se calcula el espacio libre para un Add o un resize, entonces excluye la unión de TODAS las `regions` (sin importar status) MÁS la unión de TODAS las `reservations` — test explícito que verifica que un Add nunca se coloca sobre una reserva.
- **Add sin espacio libre**: dado que el footprint de un cuboid nuevo no cabe en ningún espacio verdaderamente libre, cuando se intenta agregar, entonces se lanza `UvAtlasOverflowException` — nunca crece el atlas ni reempaqueta nada existente.
- **Delete con textura pintada**: dado un cuboid con ≥1 cara `PAINTED`, cuando se elimina, entonces sus 6 caras (bloque completo) pasan a `status=ORPHAN` en su fila existente de `UvRegion` — sin crear ninguna `UvReservation` nueva.
- Dado `BoxUvMath` (helper compartido, extraído de la matemática de box-unwrap ya verificada de `AlphaAutoPackStrategy`), cuando `StableUvStrategy` calcula footprints, entonces produce resultados idénticos a los que `AlphaAutoPackStrategy` calcularía para la misma geometría (test de paridad, mismo criterio que las fixtures compartidas frontend/backend ya existentes).

## Hecho
