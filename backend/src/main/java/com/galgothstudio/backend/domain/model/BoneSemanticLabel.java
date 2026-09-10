package com.galgothstudio.backend.domain.model;

/**
 * Etiqueta semántica de un bone concreto (ej. "cabeza", "torso frontal")
 * dentro de un {@link TexturePlan} (ticket 052). `boneId` referencia el
 * id REAL (UUID) de un {@link Bone} del modelo actual -- `boneName` viaja
 * junto a propósito (mismo criterio que `TextureGenerationSheet.boneId`/
 * `boneName`, Diseño técnico §11) para que un consumidor downstream no
 * necesite un join contra el modelo solo para loguear/mostrar un nombre
 * legible.
 */
public record BoneSemanticLabel(String boneId, String boneName, String semanticLabel) {
}
