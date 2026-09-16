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
