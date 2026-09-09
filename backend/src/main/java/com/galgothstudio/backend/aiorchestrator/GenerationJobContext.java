package com.galgothstudio.backend.aiorchestrator;

import java.util.UUID;

/**
 * Datos del mob/referencia YA EXTRAÍDOS de las entidades JPA (ticket
 * 029) -- {@link MobGenerationService#runPipeline} corre en OTRO hilo
 * (`generationExecutor`), así que nunca recibe las entidades mismas
 * (riesgo de `LazyInitializationException`/sesión cerrada), solo estos
 * valores planos ya leídos en el hilo síncrono de {@code startGeneration}.
 * Agrupados en un record también por Sonar `S107` (máximo de parámetros).
 */
record GenerationJobContext(
		UUID jobId, UUID mobId, UUID projectId, String mobName, String baseType, UUID referenceId, String storageKey, String contentType) {
}
