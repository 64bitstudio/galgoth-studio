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
