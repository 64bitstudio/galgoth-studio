# 099 — SecondaryGeometryPlanner (IA acotada) + constraints deterministas + `semanticPart`

## Objetivo
Nace de `docs/definiciones/anatomia-por-capas-generacion-mobs.md` (HU-2b, y la parte de `semanticPart` de HU-2). Con la anatomía primaria ya resuelta de forma determinista (ticket 098), este ticket concentra la única libertad real del LLM en geometría secundaria (ropa, jirones, garras, cuernos — lo que da identidad visual a un personaje), y le pone límites duros para que nunca produzca un modelo corrupto.

**Depende de:** 098 (necesita la anatomía primaria ya fijada como contexto). **Bloqueado por:** 097. **Bloquea:** 101 (featureCoverage necesita `semanticPart` ya persistido).

## Alcance
**Incluye:**
- `SecondaryGeometryPlanner`: reemplaza el alcance actual de `GeometryPlannerService` en lo que hace a geometría — recibe anatomía primaria fijada + `features`/`materials` de `ModelIntent` + presupuesto restante (de `geometryDetail`, ver ticket 100), y solo puede emitir operaciones de geometría secundaria (nunca redefinir/eliminar cuboides primarios).
- Campo `semanticPart` en `Cuboid` (backend `domain/model/Cuboid.java` + espejo `MobProjectModel.ts` + `contracts/schemas/mob-project-model.schema.json`) — cambio aditivo de schema, señalado explícitamente por tocar un contrato compartido (regla de equipo #9).
- Constraints deterministas en `GeometryEngine` para geometría secundaria: rechaza si referencia `boneId` inexistente, excede tamaño máximo razonable relativo al template, cae fuera del bounding box del personaje, está a distancia irrazonable del pivot del bone padre, o tiene dimensiones inválidas (cero/negativas/NaN/Infinity). Cuboides huérfanos se rechazan explícitamente, nunca se cuelgan de un bone arbitrario.
- Operaciones rechazadas no fallan el job completo: se aplican las válidas y se registra un `generationWarning` por cada rechazo con la razón concreta.

**No incluye:**
- Presupuesto `geometryDetail` en sí y su conexión desde la UI (ticket 100) — este ticket recibe el presupuesto como parámetro, no lo construye.
- Taxonomía `SemanticPartCategory` enum cerrada (ticket 102) — este ticket usa `semanticPart` como campo string por ahora; la categorización cerrada llega en 102 y puede requerir un ajuste menor de tipo, ya anticipado.

## Criterios de aceptación (TDD)
- Dado que la anatomía primaria ya existe, cuando se invoca `SecondaryGeometryPlanner`, entonces recibe explícitamente `features`/`materials` de `ModelIntent` y presupuesto restante, y solo emite operaciones de geometría secundaria.
- Dado el modelo resultante, cuando se inspecciona cualquier cuboid (primario o secundario), entonces tiene `semanticPart` no vacío persistido.
- Dado una operación que intenta modificar/eliminar un cuboid primario, cuando `GeometryEngine` la procesa, entonces la rechaza en vez de aplicarla.
- Dado un cuboid secundario propuesto con `boneId` inexistente, tamaño excesivo, fuera de bounding box, distancia irrazonable al pivot, o dimensiones inválidas, cuando se valida, entonces se rechaza — con test específico por cada caso.
- Dado un cuboid secundario huérfano, cuando se valida, entonces se rechaza explícitamente (no se cuelga de un bone arbitrario).
- Dado que una o más operaciones se rechazan, cuando termina la generación, entonces el job no falla completo y cada rechazo queda en `generationWarnings` con su razón.

## Hecho
