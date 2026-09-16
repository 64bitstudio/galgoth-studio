package com.galgothstudio.backend.aiorchestrator;

import com.galgothstudio.backend.domain.model.GeometryDetail;
import com.galgothstudio.backend.domain.model.TextureResolution;
import java.util.UUID;

/**
 * Datos del mob/referencia YA EXTRAÍDOS de las entidades JPA (ticket
 * 029) -- {@link MobGenerationService#runPipeline} corre en OTRO hilo
 * (`generationExecutor`), así que nunca recibe las entidades mismas
 * (riesgo de `LazyInitializationException`/sesión cerrada), solo estos
 * valores planos ya leídos en el hilo síncrono de {@code startGeneration}.
 * Agrupados en un record también por Sonar `S107` (máximo de parámetros).
 *
 * @param geometryDetail presupuesto de detalle geométrico elegido en
 *                       Configuración (ticket 100) -- {@code MEDIUM} si el
 *                       caller no mandó ninguno (compatibilidad).
 * @param textureResolution TOPE de atlas elegido en Configuración (ticket
 *                          103) -- {@code MAX_128} si el caller no mandó
 *                          ninguno. Ver {@link TextureResolution}: acota,
 *                          nunca fija el tamaño.
 */
record GenerationJobContext(
		UUID jobId, UUID mobId, UUID projectId, String mobName, String baseType, UUID referenceId, String storageKey,
		String contentType, GeometryDetail geometryDetail, TextureResolution textureResolution) {
}
