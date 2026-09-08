# 007 — AutoUv frontend + fixtures compartidas

**Milestone:** M0 · **Depende de:** 006 · **HUs:** HU-16

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §6). Portar `AlphaAutoPackStrategy` a TypeScript para dar feedback inmediato en el editor manual (cada `Command` de creación/redimensión), sin ninguna petición HTTP por interacción. El backend (006) sigue siendo la autoridad final.

## Criterios de aceptación (TDD)
- Dado un mismo conjunto de geometrías de entrada, cuando se ejecuta el algoritmo en frontend (TS, Vitest) y en backend (Java, JUnit) usando el **mismo fixture set**, entonces ambos producen exactamente el mismo layout UV — un test compartido/espejado en ambos suites verifica la paridad.
- Dado un `Command` de crear/redimensionar cuboid en el editor, cuando se ejecuta, entonces la UV se calcula y aplica al draft en memoria de forma síncrona, sin llamada de red.
- Dado que el frontend calcula una UV distinta a la que luego calcula el backend para el mismo input (divergencia por bug, no por diseño), cuando corre el test de paridad de fixtures, entonces falla en CI — cualquiera de los dos lados.

## Hecho

- **`frontend/src/domain/autoUv.ts`**: espejo TS línea por línea de `AlphaAutoPackStrategy.java` (mismo box-UV-unwrap, mismo shelf-packing determinista, mismo orden de iteración) — función pura `layoutUv(cuboids, textureWidth, textureHeight)`, sin `fetch`/HTTP. `UvAtlasOverflowError` espeja los mismos 4 campos que `UvAtlasOverflowException` (Java).
- **`contracts/fixtures/uv-layout-fixture.json`** (nueva fixture compartida, ticket 007): 2 cuboids (`head` 8×8×8, `body` 8×12×4) sobre un atlas 64×64, con la UV esperada de las 6 caras calculada A MANO con la fórmula documentada (no generada corriendo el propio código bajo prueba, para que el test de paridad sea una verificación real y no una tautología). Consumida por AMBOS test suites:
  - `frontend/src/domain/__tests__/autoUv.spec.ts` (Vitest) — incluye el test de paridad contra la fixture + 5 tests propios del algoritmo (caja simple, múltiples cuboids, overflow, determinismo, síncrono/sin red).
  - `backend/src/test/java/com/galgothstudio/backend/domain/uv/AlphaAutoPackStrategyFixtureParityTest.java` (JUnit) — mismo fixture, mismas aserciones, lado Java.
- **AC #2 (Command sin red)**: no existe todavía un sistema de `Command`/editor manual real (eso es la épica de tickets 015-020, milestone M2, posterior a este M0) — el AC se satisface a nivel de la función pura: `layoutUv` es 100% síncrona (tipo de retorno no-`Promise`, sin ningún I/O), que es la propiedad que un futuro `Command` necesitará para llamarla sin red. Documentado explícitamente aquí para que no se lea como que ya existe integración de UI — cuando el ticket 016+ construya el Command real, debe importar `layoutUv` directo, no reimplementar ninguna lógica.

**Tests**: 6 nuevos en frontend (Vitest, incluye paridad) + 1 nuevo en backend (JUnit, paridad) = 7 nuevos. Verificados: `npx vitest run` (25 tests totales frontend, 0 fallos), `npx vue-tsc -b` sin errores, `npm run lint` sin errores, `npm run build` exitoso; `./gradlew build -x sonar` (49 tests totales backend, 0 fallos).

**Fuera de alcance de este ticket:** integración real con un `Command`/UI del editor manual — no existe todavía esa infraestructura (tickets 015-020). `StableUvStrategy` (Fase 3) tampoco se toca aquí, igual que en el ticket 006.
