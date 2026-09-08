# 005 — Geometry Engine (backend)

**Milestone:** M0 · **Depende de:** 004 · **HUs:** HU-14

## Objetivo
Nace de `docs/definiciones/galgoth-studio-mvp.md` (sección 9.2 del master prompt y HU-14). Implementar el motor determinista de aplicación de operaciones de geometría (whitelist cerrada), con validación de esquema atómica y resolución de `tempRef` — el único camino por el que cualquier escritura de geometría (manual server-side o IA) pasa.

## Criterios de aceptación (TDD)
- Dado una lista de operaciones que incluye `createBone`, `createCuboid`, `resizeCuboid`, `moveCuboid`, `rotateCuboid`, `setBonePivot`, `setBoneRotation`, `parentBone`, `removeCuboid`, cuando se aplican sobre un `MobProjectModel`, entonces cada una produce el efecto esperado usando el `CoordinateSystemContract` de 004.
- Dado un batch con una operación fuera de whitelist o con payload inválido, cuando se valida, entonces el batch completo se rechaza de forma atómica (ninguna operación se aplica).
- Dado un `createBone` seguido de un `createCuboid` que lo referencia por `tempRef` en el mismo batch, cuando se aplica, entonces el backend resuelve el `tempRef` a un UUID real generado por la aplicación — nunca un UUID provisto externamente.
- Dado un `resizeCuboid`/`moveCuboid` que produciría dimensiones negativas o cero, cuando se valida, entonces se rechaza antes de aplicarse.
- Dado un `removeCuboid` sobre un cuboid con referencias activas, cuando se aplica, entonces las referencias padre/hijo del modelo resultante siguen siendo válidas.

## Hecho

Implementado en `backend/src/main/java/com/galgothstudio/backend/domain/geometry/`:

- **`GeometryOperation`**: interfaz sellada (`sealed`/`permits`) con las 9 operaciones de la whitelist como `record`s (`CreateBone`, `CreateCuboid`, `ResizeCuboid`, `MoveCuboid`, `RotateCuboid`, `SetBonePivot`, `SetBoneRotation`, `ParentBone`, `RemoveCuboid`), discriminadas por el campo JSON `"op"` (Jackson `@JsonTypeInfo`/`@JsonSubTypes`), formato fiel al ejemplo del master prompt §9.3 (`{"op": "resizeCuboid", "target": "...", "scale": [...]}`).
- **`GeometryEngine.apply(model, operaciones)`**: función pura y atómica — construye un working-copy mutable (bones/cuboids por id, regiones UV), aplica cada operación validándola en el momento; si CUALQUIERA falla, lanza `GeometryValidationException` y el `MobProjectModel` de entrada nunca se toca (los records de dominio son inmutables y las colecciones de entrada se copian antes de mutar).
- `tempRef`: cada `createBone`/`createCuboid` requiere un `tempId` único en el batch; cualquier otra operación puede referenciar tanto un id real ya existente como un `tempId` definido *antes* en el mismo batch (no forward-refs hacia adelante) — se resuelve siempre a un UUID generado por `UUID.randomUUID()`, nunca a un valor externo.
- `resizeCuboid` escala `(to-from)` por eje manteniendo el CENTRO fijo (`scale` debe ser `> 0` en los 3 ejes). `moveCuboid` traslada `from`+`to`+`origin` por igual (traslación rígida — **decisión de producto confirmada explícitamente con el Product Owner vía AskUserQuestion durante la implementación**, ver nota abajo). `rotateCuboid` SUMA un delta a `cuboid.rotation` (no reemplaza). `setBonePivot`/`setBoneRotation` SÍ reemplazan de forma absoluta (de ahí "set", a diferencia de los verbos relativos `resize`/`move`/`rotate`).
- `parentBone` rechaza auto-parentarse y cualquier reasignación que crearía un ciclo en la jerarquía de bones (recorre la cadena de padres del nuevo padre propuesto).
- `removeCuboid` elimina también en cascada cualquier `uv.regions[]` que referenciara el cuboid eliminado por `cuboidId`, para que el modelo resultante nunca quede con una referencia colgante (AC #5).
- Toda la matemática de rotación reutiliza `CoordinateSystem` (ticket 004) — el Geometry Engine no reimplementa transformación de coordenadas propia.

**Tests**: 21 tests nuevos (`GeometryEngineTest`, 17; `GeometryOperationJsonTest`, 4), 100% en verde — verificados corriendo `./gradlew test --tests "com.galgothstudio.backend.domain.geometry.*"` y el build completo `./gradlew build -x sonar` (38 tests totales del módulo backend, 0 fallos). Los tests de rotación (`rotateCuboid`, `setBonePivot`+`setBoneRotation`) reutilizan el mismo fixture compartido `contracts/fixtures/coordinate-system-fixture.json` que ya verifica `CoordinateSystemContract` en TS y Java, en vez de reimplementar valores esperados a mano.

**Decisión de producto tomada durante la implementación (vía `AskUserQuestion`, no asumida):** el AC #4 agrupa `resizeCuboid` y `moveCuboid` como operaciones que podrían producir dimensiones negativas/cero. Con una traslación rígida pura, `moveCuboid` nunca puede violar esa condición por construcción (el AC queda como guarda defensiva compartida, no como caso alcanzable end-to-end). Se confirmó con el Product Owner que `moveCuboid` es traslación rígida (no "arrastrar una esquina"); la guarda de dimensión positiva se implementó igual como función compartida (`GeometryEngine.validatePositiveDimensions`, package-private) invocada tras `moveCuboid` también, y se testea directamente para dejar constancia de que existe y funciona, documentando en el propio test por qué no es alcanzable vía un `moveCuboid` real.

**Hallazgo real (no un bug de producción, pero sí un gotcha reusable):** `ObjectMapper.writeValueAsString(Object)` sobre una variable Java de tipo `List<GeometryOperation>` pierde el parámetro genérico por *type erasure* — serializa cada elemento por su clase concreta en runtime sin pasar por el `TypeSerializer` de la interfaz sellada, y el campo discriminador `"op"` desaparece silenciosamente del JSON (round-trip roto). Se descubrió al escribir el test de round-trip de las 9 operaciones. Fix: usar `mapper.writerFor(new TypeReference<List<GeometryOperation>>(){})` (igual que ya se hacía del lado de la lectura) en vez de `writeValueAsString` directo. Relevante para cualquier controlador REST futuro (tickets 018/028/031) que devuelva una lista de `GeometryOperation` — dejar esta nota aquí para que no se repita el mismo error al exponer el endpoint.

**Fuera de alcance de este ticket (según su propio título "backend" y objetivo):** no se agregó endpoint REST — el motor es la pieza de dominio pura; su exposición vía API llega con el primer ticket que realmente la necesite (transformaciones manuales del editor, ticket 018, o el flujo de edición IA, tickets 028/031).
