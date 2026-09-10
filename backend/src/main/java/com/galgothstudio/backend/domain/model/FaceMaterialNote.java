package com.galgothstudio.backend.domain.model;

/**
 * Nota de material para una cara ({@link FaceName}) de un bone concreto
 * dentro de un {@link TexturePlan} (ticket 052) -- ej. bone "torso",
 * cara `NORTH` ("frente"): "cuero desgastado con parches". La
 * granularidad es (bone, cara) y no (cuboid, cara): un bone puede tener
 * varios cuboids (ej. antebrazo+mano del mismo bone) y, para el
 * propósito de `TextureGenerationSheet` (054, que genera UNA imagen
 * coherente por bone), la orientación relevante es la del bone
 * completo, igual que `CuboidFacePlacement.orientationHint` se resuelve
 * "desde FaceName + rotación del bone" (Diseño técnico §11).
 */
public record FaceMaterialNote(String boneId, String boneName, FaceName face, String note) {
}
