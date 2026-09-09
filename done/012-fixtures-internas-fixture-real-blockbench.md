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

## Hecho

**AC #1 (snapshot interno) — cumplido:**
- `contracts/fixtures/model-spec-example.json` expandido con 3 casos borde nuevos: bone con rotación no trivial (`arm_right`, `[0,0,-18]`), 2 cuboids nuevos (`torso_main` parentado directo al bone RAÍZ `body` — antes ningún cuboid del fixture colgaba directo de la raíz —, `arm_right_upper` con su propia rotación no trivial `[0,0,-18]`, verificando que rotación de bone Y de cuboid coexisten sin interferirse). Verificado que ningún test existente (round-trip TS/Java, schema TS/Java) asume conteos fijos antes de expandirlo — 0 regresiones.
- `backend/src/test/resources/fixtures/bbmodel-golden/model-spec-example.bbmodel.json`: snapshot generado ejecutando el exportador una vez, REVISADO A MANO antes de aprobarlo (confirmado: `format_version:"5.0"`, `groups[]` plano, `outliner[]` con la jerarquía real incluyendo `torso_main` como hijo directo del uuid `body`, rotación `-18` presente donde corresponde y AUSENTE donde es cero). `BBModelExporterV5SnapshotTest` compara estructuralmente contra este archivo (`JSONAssert.assertEquals(..., STRICT)`) — un cambio no intencional en el exportador lo rompe.

**AC #3 y #4 (fixture real `carcomido_minecraft_cuboids.bbmodel` + parsing test-only) — cumplido:**
- `backend/.../domain/export/blockbenchreal/BlockbenchBbmodelTestParser.java` (paquete-visible, sin ningún import desde `domain/export`, `domain/geometry` ni ningún paquete de producción — solo lo usan tests en el mismo paquete): reimplementación en Java, INDEPENDIENTE del script Python del ticket 008, del mismo algoritmo (recorrido de `outliner`, manejo de rotación escalar+eje de cubes viejos vs. array de groups, exclusión de elementos huérfanos). Confirma de forma cruzada, en un lenguaje distinto, el mismo hallazgo del ticket 008 (1 de 25 elementos —`crack_head`— huérfano).
- `BlockbenchRealFixtureConformanceTest`: el `.bbmodel` real parsea a un `MobProjectModel` válido contra el JSON Schema formal, y ese modelo se re-exporta con nuestro propio `BBModelExporterV5` sin errores — prueba de punta a punta que el dominio interno representa un archivo real sin pérdida relevante.

**AC #2 (archivos `.bbmodel` reales mínimos en v5) — cumplido (2026-09-09), tras un bloqueo real:**
- Un primer paquete de 10 archivos entregado por el PO resultó ser generado programáticamente ("`generated_by: ChatGPT fixture pack`", el propio README del paquete lo admitía) -- se le señaló explícitamente que esto anularía el propósito del ticket (solo probaría consistencia con nuestra propia spec, no con Blockbench real) y se le pidió el paso real. El PO abrió cada archivo en Blockbench 5.x real, lo guardó, y usó "Export Legacy Project" para el v4 (ver ticket 014) -- confirmado antes de aceptarlos: los archivos finales tienen campos idiosincrásicos reales de Blockbench que nuestro propio exportador nunca produce (`unhandled_root_fields`, `multi_file_ruleset`, `allow_mirror_modeling`, `mirror_uv`, `selected`, metadata completa de textura), evidencia de que vienen de la app real.
- 5 casos mínimos, en `backend/src/test/resources/fixtures/blockbench-real/v5/`: `01_cuboid_simple`, `02_parent_child_hierarchy`, `03_pivot_rotation`, `04_multiple_cuboids`, `05_uv_real_texture` (+ `textures/fixture_real_texture.png`, la textura fuente real).
- `BlockbenchBbmodelTestParser` extendido para soportar la forma v5 de `outliner` (separado de `groups[]` por `uuid` -- antes solo soportaba la forma v4 embebida) y para detectar `FormatVersion` real del archivo (antes hardcodeado a V4).

**Hallazgo real de conformidad, exactamente el tipo que este ticket existe para encontrar:** el fixture `01_cuboid_simple` (real, de Blockbench) tiene el cuboid colgado DIRECTO de la raíz del `outliner`, sin ningún group/bone -- Blockbench lo permite. Nuestro dominio (`Cuboid.boneId`, ticket 004) exige que TODO cuboid pertenezca a un bone, invariante necesaria para animación. El parser (test-only) sintetiza un bone raíz implícito con pivot/rotación cero para representar este caso sin perder geometría relevante (posición/tamaño/rotación/caras exactos) -- documentado explícitamente en el código, nunca oculto. No requiere cambios en el dominio de producción: el producto siempre construye mobs con bones reales (el editor/wizard nunca genera un cuboid sin bone), este caso solo aparece en un archivo `.bbmodel` externo hecho a mano.

**AC #5 (CI falla ante incompatibilidad)** — cumplido: `BlockbenchRealFixturePackConformanceTest` (10 archivos × validación de schema + reexport sin errores, más 5 tests dedicados por caso) corre dentro de `./gradlew build`, igual que el snapshot interno y `carcomido_minecraft_cuboids.bbmodel`.

**Tests**: 15 nuevos en este cierre (10 parametrizados + 5 dedicados por caso), 216 tests totales en el módulo backend, 0 fallos.

**Épica 009 desbloqueada para M6** (junto con 014, ver su propio Hecho) -- solo dependía de este AC #2.
