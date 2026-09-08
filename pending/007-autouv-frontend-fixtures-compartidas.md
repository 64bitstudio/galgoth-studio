# 007 — AutoUv frontend + fixtures compartidas

**Milestone:** M0 · **Depende de:** 006 · **HUs:** HU-16

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §6). Portar `AlphaAutoPackStrategy` a TypeScript para dar feedback inmediato en el editor manual (cada `Command` de creación/redimensión), sin ninguna petición HTTP por interacción. El backend (006) sigue siendo la autoridad final.

## Criterios de aceptación (TDD)
- Dado un mismo conjunto de geometrías de entrada, cuando se ejecuta el algoritmo en frontend (TS, Vitest) y en backend (Java, JUnit) usando el **mismo fixture set**, entonces ambos producen exactamente el mismo layout UV — un test compartido/espejado en ambos suites verifica la paridad.
- Dado un `Command` de crear/redimensionar cuboid en el editor, cuando se ejecuta, entonces la UV se calcula y aplica al draft en memoria de forma síncrona, sin llamada de red.
- Dado que el frontend calcula una UV distinta a la que luego calcula el backend para el mismo input (divergencia por bug, no por diseño), cuando corre el test de paridad de fixtures, entonces falla en CI — cualquiera de los dos lados.
