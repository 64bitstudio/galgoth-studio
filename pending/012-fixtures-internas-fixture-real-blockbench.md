# 012 — Fixtures internas + fixture real de Blockbench (conformidad)

**Milestone:** M1 (mínimo v5) y M6 (v4 completo, junto a 014) · **Depende de:** 010 · **HUs:** HU-21 · **Épica:** 009

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §7 y Addendum). Además de los snapshots generados por nuestro propio `BBModelExporterV5`, agregar como **fixtures de conformidad** archivos `.bbmodel` REALES exportados a mano desde una versión soportada de Blockbench: como mínimo cuboid simple, jerarquía parent/child, pivots/rotaciones, múltiples cuboides, UV/textura, en formato v5 (v4 se cubre en 014).

**Aclaración explícita de alcance:** este ticket **no** implementa un `BBModelImporter` como feature del producto. Las fixtures reales son solo material de prueba de conformidad — se permite código de parsing/adaptadores **exclusivamente dentro del test suite**, para poder comparar la salida de nuestro exportador contra estos archivos reales. Ese código de test no es una capacidad de importación expuesta a usuarios ni a otros módulos del backend.

## Criterios de aceptación (TDD)
- Dado `model_spec_example.json` expandido con casos borde, cuando corren los tests, entonces `BBModelExporterV5` se compara estructuralmente contra un snapshot aprobado (fixture interna).
- Dado los archivos `.bbmodel` reales mínimos (cuboid simple, jerarquía, pivots, múltiples cuboides, UV/textura, v5), cuando se agregan a `backend/src/test/resources/fixtures/blockbench-real/`, entonces el dominio interno puede representarlos sin pérdida relevante, verificado por el parsing de test.
- Dado `carcomido_minecraft_cuboids.bbmodel`, cuando corre el test correspondiente, entonces se usa como fixture adicional de conformidad.
- Dado el código de parsing de fixtures reales, cuando se revisa el árbol del proyecto, entonces vive exclusivamente bajo `src/test/` — ningún paquete de producción (`export`, `project`, etc.) lo importa ni lo expone como funcionalidad.
- Dado un cambio en el exportador que rompe compatibilidad con cualquiera de los dos conjuntos (snapshots internos o fixtures reales), cuando corre CI, entonces el pipeline falla.
