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

## Hecho (parcial — ticket NO cerrado, ver bloqueo)

**AC #1 (snapshot interno) — cumplido:**
- `contracts/fixtures/model-spec-example.json` expandido con 3 casos borde nuevos: bone con rotación no trivial (`arm_right`, `[0,0,-18]`), 2 cuboids nuevos (`torso_main` parentado directo al bone RAÍZ `body` — antes ningún cuboid del fixture colgaba directo de la raíz —, `arm_right_upper` con su propia rotación no trivial `[0,0,-18]`, verificando que rotación de bone Y de cuboid coexisten sin interferirse). Verificado que ningún test existente (round-trip TS/Java, schema TS/Java) asume conteos fijos antes de expandirlo — 0 regresiones.
- `backend/src/test/resources/fixtures/bbmodel-golden/model-spec-example.bbmodel.json`: snapshot generado ejecutando el exportador una vez, REVISADO A MANO antes de aprobarlo (confirmado: `format_version:"5.0"`, `groups[]` plano, `outliner[]` con la jerarquía real incluyendo `torso_main` como hijo directo del uuid `body`, rotación `-18` presente donde corresponde y AUSENTE donde es cero). `BBModelExporterV5SnapshotTest` compara estructuralmente contra este archivo (`JSONAssert.assertEquals(..., STRICT)`) — un cambio no intencional en el exportador lo rompe.

**AC #3 y #4 (fixture real `carcomido_minecraft_cuboids.bbmodel` + parsing test-only) — cumplido:**
- `backend/.../domain/export/blockbenchreal/BlockbenchBbmodelTestParser.java` (paquete-visible, sin ningún import desde `domain/export`, `domain/geometry` ni ningún paquete de producción — solo lo usan tests en el mismo paquete): reimplementación en Java, INDEPENDIENTE del script Python del ticket 008, del mismo algoritmo (recorrido de `outliner`, manejo de rotación escalar+eje de cubes viejos vs. array de groups, exclusión de elementos huérfanos). Confirma de forma cruzada, en un lenguaje distinto, el mismo hallazgo del ticket 008 (1 de 25 elementos —`crack_head`— huérfano).
- `BlockbenchRealFixtureConformanceTest`: el `.bbmodel` real parsea a un `MobProjectModel` válido contra el JSON Schema formal, y ese modelo se re-exporta con nuestro propio `BBModelExporterV5` sin errores — prueba de punta a punta que el dominio interno representa un archivo real sin pérdida relevante.

**AC #2 — BLOQUEADO, decisión explícita del Product Owner (2026-09-08):** los archivos `.bbmodel` reales MÍNIMOS en v5 (cuboid simple, jerarquía, pivots, múltiples cuboides, UV/textura) que pide este AC deben exportarse a mano desde una instalación real de Blockbench — este entorno no tiene Blockbench disponible para generarlos de verdad, y fabricarlos a mano sin haber pasado por la app real anularía el propósito del ticket (detectar divergencias reales entre nuestro exportador y Blockbench). Se le presentó la situación a Marco vía `AskUserQuestion`; decidió explícitamente avanzar con lo demás y dejar esto marcado como bloqueo real — **este ticket permanece en `in-process/`, NO se mueve a `done/`**, hasta que esos archivos existan en `backend/src/test/resources/fixtures/blockbench-real/`.

**AC #5 (CI falla ante incompatibilidad)** — cumplido de forma indirecta: `BBModelExporterV5SnapshotTest` y `BlockbenchRealFixtureConformanceTest` corren dentro de `./gradlew build` (parte del pipeline normal), así que cualquier ruptura de compatibilidad con lo que SÍ existe hoy (snapshot interno + carcomido) ya falla CI. Cuando lleguen los archivos v5 mínimos, se agregan sus propios tests de conformidad al mismo `build`.

**Tests**: 5 nuevos (`BBModelExporterV5SnapshotTest`, 2; `BlockbenchRealFixtureConformanceTest`, 3), 100% en verde — 62 tests totales en el módulo backend (`./gradlew build -x sonar`), 0 fallos. Frontend también verificado (44 tests, `vue-tsc -b`, `lint` limpios) por el cambio al fixture compartido.

**Próximo paso cuando lleguen los archivos reales:** agregarlos a `backend/src/test/resources/fixtures/blockbench-real/`, extender `BlockbenchBbmodelTestParser`/un nuevo test de conformidad por archivo, y recién ahí mover este ticket a `done/`.
