# 116 — La métrica de relleno de bordes tiene que llegar al reporte de calidad

## Objetivo
El ticket 114 pedía que quedara registrado cuántas caras necesitaron relleno de bordes, para poder ver si el proveedor de imágenes empeora con el tiempo. Quedó como log del job, no en el reporte — desvío señalado explícitamente al cerrar el 114, y el Product Owner decidió que sí se lleve al reporte.

La razón por la que no se hizo en el 114 sigue en pie y es el trabajo real de este ticket: `ModelGenerationQualityReport` se calcula en el pipeline de **geometría** y no tiene forma de saber qué pasó al texturizar. Hace falta un canal de advertencias de textura que hoy no existe.

**Depende de:** 114 (produce la métrica), y se cruza con el 104 (que definió el canal de `generationWarnings` para geometría — hay que decidir si se reutiliza o se hace el equivalente de textura).

## Alcance
**Incluye:**
- Decidir y documentar dónde vive la métrica de textura: extender el reporte existente, o un reporte/canal propio del job de textura. La decisión y su tradeoff quedan escritos, no implícitos.
- Que el conteo del 114 (`edgesFilled` / `edgesSkipped` agregados por job) llegue a ese canal con sus números concretos, no solo al log.
- Que sea visible donde el usuario o el equipo lo pueda leer, no solo en la base.

**No incluye:**
- Cambiar el comportamiento del relleno (el 114 quedó cerrado y medido).
- Rediseñar `generationWarnings` de geometría.

## Criterios de aceptación (TDD)
- Dado un job de textura donde N caras necesitaron relleno y M tenían banda demasiado ancha, cuando termina, entonces esos dos números quedan consultables fuera del log.
- Dado un job donde ninguna cara necesitó relleno, cuando termina, entonces la métrica existe y vale cero — la ausencia de advertencias no se confunde con "no se midió".
- El lugar elegido para la métrica queda documentado en `/docs` con el porqué de no haberla puesto en `ModelGenerationQualityReport`.

## Hecho

### Dónde vive la métrica, y por qué ahí
Un canal propio: **`ai_jobs.warnings_jsonb`** (migración `V9`), expuesto como campo **aditivo** `warnings` en `GET /api/jobs/{jobId}/result` y `GET /api/jobs/{jobId}/texture-result`.

Las dos alternativas se evaluaron y se descartaron con razón concreta, no por preferencia:

- **`ModelGenerationQualityReport`** (lo que el ticket proponía): se calcula en el pipeline de **geometría** y no tiene forma de saber qué pasó al texturizar. Además, las advertencias **no son una métrica del modelo**: son el registro de decisiones que el pipeline tomó sobre lo que la IA propuso. Son cosas distintas y merecen lugares distintos.
- **Reusar `ai_job_events`**: lo consume la UI de progreso, que mapea `stage` a pasos vía `findStageIndex` (`frontend/src/ai/generationStages.ts`) y devuelve **-1** para un stage desconocido. Un evento de advertencia le **reiniciaría la barra de progreso** al usuario. Se verificó en el código antes de descartarlo.

### La ausencia de advertencias no se confunde con "no se midió" (criterio #2)
Un job que **corre** siempre escribe algo: `[]` cuando no hubo advertencias. Nunca deja `NULL`. Ese es el criterio de aceptación y se cumple.

La distinción entre *"no hubo"* y *"no se midió"* quedó en la **base**: `warnings_jsonb` es nullable y `NULL` está reservado para los jobs anteriores a este ticket, que sirve para analizar el histórico. **La API normaliza ambos a `[]`.**

Esa última parte fue una corrección sobre la marcha: la primera versión propagaba `null` hasta la API para que el consumidor también viera la diferencia, y el Quality Gate de Sonar la rechazó (S1168, "return an empty collection instead of null") en 4 lugares. La regla tiene razón: devolver colecciones nulas obliga a todos los consumidores a defenderse. Se cedió ahí y se conservó la distinción donde de verdad tiene valor.

### Qué se registra hoy
`GenerationWarning(type, detail, subject)` con `type` como **enum cerrado** — no string libre — para poder filtrar sin volver a parsear texto:
- `GEOMETRIA_ENGROSADA` / `GEOMETRIA_RECHAZADA` (tickets 121 y 099).
- `BORDES_RELLENADOS` / `BANDA_NEGRA_ANCHA` (ticket 114, que es el caso que originó este ticket).
- `CONTENIDO_SOSPECHOSO` (ticket 102).

Los `log.info` siguen existiendo: sirven para diagnosticar en el momento. Lo que cambió es que ya **no son el único lugar** donde queda registro. Tres tickets distintos (099, 102, 114) habían dejado escrito el mismo pendiente, y el 121 se quedó sin poder cerrar un criterio de aceptación por esto.

### Cambio de contrato: aditivo
Los dos endpoints ganan un campo; ningún consumidor existente se rompe. Ambas vistas conservan una **sobrecarga de compatibilidad** que deja `warnings` en `null`. Documentado en `docs/API.md` con la tabla de significados y el porqué de la decisión.

### Tests
4 tests nuevos en `GenerationWarningTest`, incluido el que fija que `null` y `[]` no se confundan al serializar. `SchemaMigrationReversibilityTest` actualizado a la versión 9 — ese test existe justamente para fijar la versión vigente, así que actualizarlo es su mantenimiento esperado, no un parche. Backend completo: **607/607 en verde**.

### Pendiente honesto
La métrica es consultable por API, que es lo que el ticket pedía. **No se construyó UI** para mostrarla: no estaba en el alcance y no hay criterio de aceptación que lo pida. Si querés que el usuario las vea en pantalla, es un ticket aparte.
