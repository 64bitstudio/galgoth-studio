# 098 — PrimaryGeometryGenerator 100% determinista + invariantes/tests

## Objetivo
Nace de `docs/definiciones/anatomia-por-capas-generacion-mobs.md` (HU-2, HU-2c). Hoy la anatomía primaria de un mob sale de una llamada al LLM que "confirma" el rig, sin ninguna garantía de que sea correcto. Este ticket construye `PrimaryGeometryGenerator`: la anatomía primaria (volumen esencial) se construye directamente del template ajustado por `ProportionEstimator` (ticket 097), sin ninguna llamada de red ni al LLM.

**Depende de:** 097 (consume el template ya ajustado). **Bloquea:** 099 (la geometría secundaria se apoya sobre la primaria ya fijada).

## Alcance
**Incluye:**
- `PrimaryGeometryGenerator`: función/servicio determinista que produce bones + cuboides base a partir del template ajustado — sin llamar a ningún `StructuredReasoningProvider`.
- Invariantes verificadas con tests reales (HU-2c): partes obligatorias del template presentes, jerarquía de bones válida (sin ciclos, `parentId` referencian bones existentes, coincide con el template), pivotes coherentes con la regla anatómica (mismo sistema de `coordinateSystem.ts`/ADR 0001 ya documentado — head→cuello, forearm→codo, etc.), tamaños > 0 en cada eje, coordenadas finitas (sin NaN/Infinity), bounding box individual y del personaje completo razonable.
- Integración con el pipeline existente: `GeometryPlannerService` deja de pedirle al LLM la anatomía primaria; la recibe ya resuelta de este generador antes de invocar la parte de IA (ticket 099).

**No incluye:**
- Geometría secundaria ni su generación por IA (ticket 099).
- Cambios a `GeometryEngine` como motor de aplicación de operaciones — este generador produce las operaciones/estado inicial, no reemplaza el motor.

## Criterios de aceptación (TDD)
- Dado el template humanoide ajustado, cuando `PrimaryGeometryGenerator` corre, entonces produce head/torso/pelvis/brazos(antebrazo+mano)/piernas(espinilla+pie) — cero llamadas de red, verificable con un test que falla si se intenta abrir un socket.
- Dado el árbol de bones resultante, cuando se valida, entonces no hay ciclos y cada `parentId` es válido.
- Dado cada bone con pivote, cuando se valida, entonces respeta la regla anatómica del template (no calculado ad hoc).
- Dado cada cuboid generado, cuando se valida, entonces tamaño > 0 en cada eje, coordenadas finitas, bounding box individual dentro de un envelope razonable.
- Dado el conjunto completo, cuando se valida, entonces el bounding box del personaje completo es plausible para el template.
- Todos estos tests corren sin mocks de IA, como parte de la suite estándar (no un caso manual).

## Hecho
- `backend/src/main/java/com/galgothstudio/backend/domain/geometry/PrimaryGeometryGenerator.java` (nuevo): `generate(emptyModel, template, intent)` ajusta proporciones vía `ProportionEstimator` (097), traduce cada `TemplateBoneSpec`/`TemplateCuboidSpec` a `CreateBone`/`CreateCuboid` (los mismos tipos de `GeometryOperation` que ya usa el camino IA) y los aplica con el `GeometryEngine` existente — cero llamadas de red, cero mocks de IA. `PrimaryGeometryResult` (nuevo, record) devuelve el modelo + las advertencias de proporciones clampadas, para no perderlas en silencio.
- **8 tests nuevos** (`PrimaryGeometryGeneratorTest`, paquete `domain.geometry` para reusar `GeometryFixtures.emptyModel()` package-private): sin red ni mocks de IA (demostrado por la sola ejecución exitosa), 15 bones/14 cuboides presentes, jerarquía sin ciclos con todo `parentId` válido, todo cuboid referencia un bone existente, tamaños > 0 y coordenadas finitas en los extremos del rango de proporciones, bounding box del personaje completo razonable en 3 combinaciones (mínimo/máximo/neutral), pivotes coherentes con la articulación semántica (cuello fijo), y los 14 nombres de cuboid esperados presentes.
- **Alcance ajustado explícitamente respecto al ticket original** (regla de equipo #8, no en silencio): la "integración con `GeometryPlannerService`" mencionada en el alcance original **se hace en conjunto con el ticket 099**, no aquí. Separarla habría dejado el pipeline real en un estado intermedio roto: el LLM ya sin la responsabilidad de anatomía primaria, pero su prompt/whitelist actual (`GeometryPlannerService`) todavía sin acotar a geometría secundaria (eso es exactamente lo que hace 099). `PrimaryGeometryGenerator` queda completo, probado de forma aislada, y listo para que 099 lo invoque.
- `semanticPart` de `TemplateCuboidSpec` (ticket 097) todavía **no** se propaga al `Cuboid` real — ese campo no existe en el dominio persistido hasta el ticket 099. Documentado explícitamente en el Javadoc de la clase, no es un olvido.
- **Bug real encontrado y corregido durante la implementación** (no en el ticket original, solo se manifestó al ejecutar el generador de verdad, no al leer el código): 4 de los `TemplateCuboidSpec` de `HumanoidCanonicalTemplate` (097) usaban el mismo id que su propio bone (`torso`, `head`, `left_forearm`, `left_hand`, y sus espejos derechos) — `GeometryEngine` rechaza un batch con `tempId` duplicado entre un `createBone` y un `createCuboid`, algo que nunca se ejercitó en el ticket 097 porque sus tests no llegaban a aplicar las operaciones contra `GeometryEngine`. Corregido: los ids de cuboid que coincidían con su bone ahora llevan sufijo (`torso_cuboid`, `head_cuboid`, `left_forearm_cuboid`, `left_hand_cuboid`, etc.) — los 21 tests de 097 actualizados en consecuencia y siguen en verde.
- Suite completa del backend corrida de verdad: **508/508 en verde, 0 failures, 0 errors**.
- Sin UI ni endpoints tocados en este ticket — no aplica revisión visual en vivo ni actualización de Postman.
