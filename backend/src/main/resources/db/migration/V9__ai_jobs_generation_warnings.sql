-- Ticket 116 -- canal de advertencias de generación, consultable fuera del
-- log.
--
-- Por qué una columna nueva y no el reporte de calidad existente:
-- `ModelGenerationQualityReport` se calcula en el pipeline de GEOMETRÍA y
-- no tiene forma de saber qué pasó al texturizar (el relleno de bordes del
-- ticket 114, los hallazgos de contenido del 102). Y las advertencias no
-- son una métrica del modelo: son el registro de decisiones que el pipeline
-- tomó sobre lo que la IA propuso.
--
-- Por qué no se reusa `ai_job_events`: ese stream lo consume la UI de
-- progreso, que mapea `stage` a pasos vía `findStageIndex` (frontend,
-- `generationStages.ts`) y devuelve -1 para un stage desconocido. Meter
-- advertencias ahí le reiniciaría la barra de progreso al usuario. Se
-- verificó en el código antes de descartarlo, no se asumió.
--
-- Nullable a propósito: los jobs YA existentes no tienen advertencias
-- registradas, y NULL dice exactamente eso -- "no se midió". Un job nuevo
-- sin advertencias guarda `[]`, que dice otra cosa distinta: "se midió y no
-- hubo ninguna". Esa diferencia es un criterio de aceptación del ticket, no
-- un detalle de implementación.
alter table ai_jobs add column warnings_jsonb jsonb;

comment on column ai_jobs.warnings_jsonb is
    'Ticket 116 -- advertencias estructuradas del pipeline (geometria ajustada/rechazada, relleno de bordes de textura, hallazgos de contenido). NULL = job anterior al ticket, sin medir. [] = medido, sin advertencias.';
