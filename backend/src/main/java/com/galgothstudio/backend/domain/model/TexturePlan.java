package com.galgothstudio.backend.domain.model;

import java.util.List;

/**
 * Salida cruda del `VisionModelProvider` (ticket 025) tras analizar la
 * imagen de referencia YA subida en Fase 2 -- Diseño técnico §11 punto 1
 * de `docs/definiciones/galgoth-studio-fase3-textura.md`, ticket 052.
 * Contrato compartido con `contracts/schemas/texture-plan.schema.json`
 * (fuente de verdad formal, mismo criterio que {@link ModelIntent}/004).
 * Equivalente de {@link ModelIntent} para el paso de TEXTURA: en vez de
 * silueta/proporciones para planear geometría, describe etiqueta
 * semántica por bone, paleta dominante/acento del modelo, y notas de
 * material por (bone, cara) -- la entrada que {@code TextureGenerationSheetPlanner}
 * (ticket 054) usará para componer un `TextureGenerationSheet` por bone.
 *
 * <p>A diferencia de {@link ModelIntent} (que arranca de un modelo
 * VACÍO, ticket 028), este paso corre sobre un modelo con bones YA
 * reales -- {@code boneId} en {@link BoneSemanticLabel}/{@link FaceMaterialNote}
 * referencia el id real (UUID) de un {@link Bone} del modelo actual, no
 * un `tempId` transitorio.
 */
public record TexturePlan(List<BoneSemanticLabel> boneLabels, TexturePalette palette, List<FaceMaterialNote> materialNotes) {
}
