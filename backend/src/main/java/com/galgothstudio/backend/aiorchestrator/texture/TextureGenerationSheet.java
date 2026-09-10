package com.galgothstudio.backend.aiorchestrator.texture;

import java.util.List;

/**
 * Todas las caras/cuboids de UN bone que deben generarse en UNA sola
 * imagen coherente -- forma EXACTA fijada por el Diseño técnico §11 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md` (ticket 053).
 * Reemplaza el enfoque de "una llamada por cuboid": todos los
 * {@code placements} de un mismo {@code TextureGenerationSheet} salen de
 * la MISMA llamada a {@code ImageGenerationProvider.generateTextureSheet(...)}.
 *
 * <p><b>Fallback/batching (Diseño técnico §11/§21, AC del ticket 053)</b>:
 * cuando {@code sheetWidth}x{@code sheetHeight} de un bone excedería el
 * límite técnico de una sola sheet ({@link TextureGenerationSheetPlanner#MAX_SHEET_DIMENSION_PX}),
 * {@link TextureGenerationSheetPlanner#plan} divide el bone en N
 * sub-sheets y devuelve {@code List<TextureGenerationSheet>} de tamaño
 * N &gt; 1 -- cada elemento de esa lista ES una de las partes, y su
 * índice (0-based) + el tamaño de la lista son la identificación
 * explícita que el ticket exige ("ej. un índice de parte") para que 054
 * pueda construir {@code stage=generando_bone_X (parte <índice+1>/<size>)}.
 * Deliberadamente NO se agregan campos {@code partIndex}/{@code partsTotal}
 * a este record -- el Diseño técnico §11 y el propio ticket 053 fijan su
 * forma EXACTA (los 9 campos de abajo, sin más); la identificación de
 * parte vive en la forma de la LISTA que devuelve el planner, no en el
 * record. Reportado explícitamente para VoBo del Product Owner: si 054
 * necesita el índice DENTRO del propio record (p. ej. para persistirlo
 * suelto en un evento SSE sin acarrear la lista completa), es una
 * extensión aditiva de ese ticket, no de este.
 *
 * @param boneId           id REAL del bone.
 * @param boneName         nombre del bone (viaja junto para no forzar un join, mismo criterio que {@link com.galgothstudio.backend.domain.model.BoneSemanticLabel}).
 * @param placements       caras/cuboids de este bone (o de esta parte, si hubo batching) -- nunca solapados entre sí (ver {@link TextureGenerationSheetPlanner}).
 * @param semanticLabel    del {@code TexturePlan}, ej. "cabeza", "torso frontal".
 * @param dominantPalette  del {@code TexturePlan} (paleta dominante/acento), como texto descriptivo.
 * @param materialNotes    del {@code TexturePlan}, notas de material de las caras de este bone.
 * @param referenceImageId la misma imagen de referencia ya subida en Fase 2.
 * @param sheetWidth       ancho (px) de la imagen temporal a generar para esta sheet/parte.
 * @param sheetHeight      alto (px) de la imagen temporal a generar para esta sheet/parte.
 */
public record TextureGenerationSheet(
		String boneId,
		String boneName,
		List<CuboidFacePlacement> placements,
		String semanticLabel,
		String dominantPalette,
		String materialNotes,
		String referenceImageId,
		int sheetWidth,
		int sheetHeight) {
}
