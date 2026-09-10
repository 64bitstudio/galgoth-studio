# 043 — POST /geometry/apply — confirmación de resize, commit en pointerup

**Milestone:** M7 · **Depende de:** 018, 020, 041 · **HUs:** HU-33 · **Épica:** L (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (Diseño técnico §2 y §15). Cierra el Hallazgo B heredado de Fase 1+2: el editor manual calcula geometría/UV 100% client-side sin que el backend lo revalide. Nuevo endpoint síncrono `POST /api/mobs/{mobId}/geometry/apply` (`MobGeometryController`, `project/api/` + `project/geometry/`, mismo patrón que `project/draft`), que ejecuta `GeometryEngine.apply`/`UvLayoutSelector` server-side — SOLO para `createCuboid`/`resizeCuboid`/`removeCuboid`. `moveCuboid`/`rotateCuboid`/pivot no afectan UV y siguen 100% client-side, sin cambios.

## Criterios de aceptación (TDD)
- Dado `POST /api/mobs/{mobId}/geometry/apply` con una operación `resizeCuboid` que no afecta ninguna cara `PAINTED`, cuando se invoca, entonces aplica la operación server-side (vía `GeometryEngine`/`UvLayoutSelector` de 041) y devuelve 200 con la geometría/UV resultante.
- Dado que la operación SÍ afecta una cara `PAINTED`, cuando se invoca sin `confirmPaintLoss`, entonces responde con el detalle de `PaintedRegionResizeConfirmationRequiredException` (4xx) sin aplicar nada.
- Dado el mismo caso reenviado con `confirmPaintLoss: true`, cuando se invoca, entonces aplica la operación (reempaqueta + `UvReservation`, mismo mecanismo de 041) y devuelve 200.
- Dado `createCuboid`/`removeCuboid`, cuando se envían a este endpoint, entonces siguen exactamente las reglas ya definidas en 041 (rechazo por `UvAtlasOverflowException` / marcado `ORPHAN`).
- Dado `moveCuboid`/`rotateCuboid`/una operación de pivot, cuando el usuario las ejecuta en el editor, entonces NUNCA disparan una llamada a este endpoint — siguen 100% client-side + autosave debounced (test de regresión: sin llamadas de red durante esas operaciones).
- **Latencia**: dado que el usuario arrastra el handle de resize de un cuboid (`pointermove`), cuando lo hace, entonces el frontend muestra un preview 100% local sin ninguna llamada de red — solo al `pointerup` se envía la ÚNICA llamada a `POST /geometry/apply` para esa operación (test de UI: cero requests de red durante N eventos `pointermove` simulados, exactamente 1 al `pointerup`).
- Dado que el backend responde con la excepción de confirmación tras el `pointerup`, cuando esto ocurre, entonces el frontend mantiene el preview visual del tamaño soltado (sin aplicar aún el efecto sobre UV) y muestra el modal de confirmación — "Confirmar" reenvía la MISMA operación con `confirmPaintLoss: true`; "Cancelar" no reenvía nada y el cuboid vuelve a su tamaño anterior.

## Hecho
