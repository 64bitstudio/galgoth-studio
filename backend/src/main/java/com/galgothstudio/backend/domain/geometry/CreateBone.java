package com.galgothstudio.backend.domain.geometry;

import com.galgothstudio.backend.domain.model.Vec3;

/**
 * Crea un bone nuevo.
 *
 * @param tempId referencia temporal única dentro del batch -- operaciones
 *               posteriores del MISMO batch pueden usarla en {@code parentId}
 *               (de otro createBone) o en {@code boneId}/{@code target} de
 *               cualquier otra operación; el motor la resuelve a un UUID
 *               real generado por la aplicación, nunca a un UUID provisto
 *               externamente (AC #3 del ticket 005).
 * @param name nombre del bone (no vacío).
 * @param parentId id real ya existente en el modelo, o el {@code tempId} de
 *                 un {@code createBone} anterior en el mismo batch, o
 *                 {@code null} para un bone raíz.
 * @param pivot punto de pivote (minecraft_pixels).
 * @param rotation rotación en grados -- ver docs/adr/0001-coordinate-system-contract.md.
 */
public record CreateBone(String tempId, String name, String parentId, Vec3 pivot, Vec3 rotation)
		implements GeometryOperation {
}
