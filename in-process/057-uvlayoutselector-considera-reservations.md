# 057 — UvLayoutSelector debe considerar reservations, no solo PAINTED/ORPHAN

**Milestone:** M11 (addendum post-Fase-3) · **Depende de:** 041, 043 · **HUs:** HU-34 · **Épica:** L (`docs/definiciones/galgoth-studio-fase3-textura.md`)

## Objetivo
Nace de `docs/definiciones/galgoth-studio-fase3-textura.md` (addendum post-implementación del 10 sep 2026, Diseño técnico §2/HU-34 — corrección explícita del PO, VoBo dado tras revisión del trabajo entregado de Fase 3). `UvLayoutSelector.resolve()` decide la estrategia de layout mirando únicamente si `previousLayout.regions()` contiene algún `PAINTED`/`ORPHAN`, ignorando `previousLayout.reservations()`. Esto es un bug lógico real: un resize confirmado sobre la única región `PAINTED` la deja `UNPAINTED` y crea una `UvReservation` para el rect abandonado — si esa era la única región pintada, el layout resultante queda con `PAINTED=0`/`ORPHAN=0`/`reservations>0`, y la regla actual elegiría incorrectamente `AlphaAutoPackStrategy` para el siguiente `Add`, que no conoce las reservas y podría reempaquetar libremente encima del rect reservado.

## Criterios de aceptación (TDD)
- Encapsular la regla en un único método `requiresStableLayout(UvLayout previousLayout): boolean` (o equivalente privado bien nombrado) en `UvLayoutSelector`.
- Regla corregida: `StableUvStrategy` si existe al menos una región `PAINTED`, O existe al menos una región `ORPHAN`, O `previousLayout.reservations()` no está vacío. `AlphaAutoPackStrategy` únicamente cuando las tres condiciones son falsas a la vez.
- Test A: `PAINTED=0`, `ORPHAN=0`, `reservations=1` → se resuelve `StableUvStrategy`.
- Test B: todas las regiones `UNPAINTED`, `reservations=0` → se resuelve `AlphaAutoPackStrategy` (comportamiento sin cambios respecto a Fase 1+2).
- Test C: un resize confirmado de la única región `PAINTED` crea una `UvReservation`; el siguiente `Add` sobre ese layout usa `StableUvStrategy` y jamás ocupa el rect reservado (test de integración/secuencia, no solo unitario sobre `resolve()`).
- Ningún test existente de `UvLayoutSelectorTest`/`StableUvStrategyTest`/`GeometryEngine`/`MobGeometryControllerTest` se debilita ni se ignora para forzar verde.
- Actualizar el comentario Javadoc de `UvLayoutSelector` (la regla de decisión documentada ahí queda desactualizada tras este fix).

## Hecho
