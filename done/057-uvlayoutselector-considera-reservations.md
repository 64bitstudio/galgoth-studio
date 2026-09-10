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

- **Documento de definición**: aplicado el patch aprobado por el PO a `docs/definiciones/galgoth-studio-fase3-textura.md` (párrafo de addendum en el header de VoBo, bloque "Hallazgo real -- corregido" + regla corregida en Diseño técnico §2, criterio de aceptación nuevo en HU-34, 2 etiquetas del diagrama Mermaid, nota en el ítem 2 e ítem 18 nuevo de "Impacto estimado").
- **Fix**: `backend/src/main/java/com/galgothstudio/backend/domain/uv/UvLayoutSelector.java` -- extraído el método `requiresStableLayout(UvLayout)` que ahora considera `previousLayout.reservations()` además de `PAINTED`/`ORPHAN`: `StableUvStrategy` si cualquiera de las tres condiciones se cumple, `AlphaAutoPackStrategy` solo si las tres son falsas. `resolve()` delega en el método nuevo. Javadoc de la clase actualizado con la regla corregida y el hallazgo (mismo estilo que otros Javadocs del proyecto que documentan hallazgos reales).
- **Tests (TDD real, escritos antes del fix)**: 3 tests nuevos en `UvLayoutSelectorTest` -- confirmado que A y C fallaban contra el código viejo (`./gradlew test --tests UvLayoutSelectorTest` en rojo antes del fix, en verde después):
  - Test A (`previousLayoutSinPaintedNiOrphanConReserva_delegaEnStableUvStrategy`): `PAINTED=0`/`ORPHAN=0`/`reservations=1` -> resultado idéntico a invocar `StableUvStrategy` directamente, reserva preservada.
  - Test B (`previousLayoutTodoUnpaintedSinReservations_delegaEnAlphaAutoPackStrategy`): layout limpio (`reservations=0`) -> resultado idéntico a `AlphaAutoPackStrategy` directo, comportamiento de Fase 1+2 sin cambios.
  - Test C (`resizeDeLaUnicaRegionPintadaCreaReserva_...`): secuencia end-to-end sobre el selector real -- (1) layout con la cara NORTH de "head" `PAINTED`, (2) resize confirmado (`confirmPaintLoss=true`) que la deja `UNPAINTED` y crea 1 `UvReservation` (mecanismo real de `StableUvStrategy`, no reinventado), (3) sobre ese nuevo layout se agrega un cuboid nuevo, (4) se verifica que ninguna de las 6 caras del cuboid nuevo se solapa con el rect reservado.
  - Ningún test existente se debilitó, comentó ni se ignoró.
- **Suite completa verificada en verde**: `./gradlew clean test` -- backend 412/412 tests, 0 failures, 0 errors (8/8 en `UvLayoutSelectorTest`).
- **Docs**: `docs/ARQUITECTURA.md` -- nueva entrada al final de la sección "Fase 3", explícitamente marcada como addendum post-milestone (no ticket 18 del milestone original 040-056).
- **Sin cambios de contrato de API**: `UvLayoutSelector` es lógica de dominio interna, no un endpoint -- no aplica actualización de `/postman`. `docs/API.md` ya describe `POST /geometry/apply` de forma genérica ("mismas reglas del ticket 041") sin detallar la regla exacta, por lo que no quedó desactualizado.
- **Hallazgo cerrado**: el bug era real y estaba en `dev` -- `UvLayoutSelector.resolve()` ignoraba por completo `previousLayout.reservations()`. El escenario disparador es un resize confirmado sobre la ÚNICA región `PAINTED` de un layout: queda `UNPAINTED` + 1 reserva, y la regla vieja habría elegido `AlphaAutoPackStrategy` para el siguiente `Add`, que reempaqueta TODO el atlas desde `(0,0)` sin conocer reservas -- pudiendo pisar exactamente el rect que `UvReservation` existe para proteger. Corregido sin reabrir alcance ni arquitectura del documento de Fase 3 (mismo VoBo FINAL, solo addendum).
