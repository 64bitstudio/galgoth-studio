package com.galgothstudio.backend.aiorchestrator.texture;

import java.util.List;
import java.util.UUID;

/**
 * Respuesta de `GET /api/jobs/{jobId}/texture-result` (ticket 054, HU-38)
 * -- diff Antes/Después de una propuesta de generación/regeneración de
 * textura por IA ya completada. `hasHandPaintedOverwrite=true` cuando al
 * menos una de {@code touchedFaces} tiene {@code handPaintedOverwrite=true}
 * -- señal explícita para que el frontend muestre la advertencia reforzada
 * de HU-37 AC #2 ("se sobrescribirá contenido pintado a mano -- no solo
 * generado por IA"), distinta del aviso genérico de "esto reemplaza lo
 * que había antes en estas regiones".
 */
public record TextureGenerationResultView(
		UUID jobId,
		UUID mobId,
		boolean wholeModel,
		List<String> touchedBoneIds,
		List<TouchedFace> touchedFaces,
		boolean hasHandPaintedOverwrite,
		String beforeAtlasPngBase64,
		String afterAtlasPngBase64) {
}
