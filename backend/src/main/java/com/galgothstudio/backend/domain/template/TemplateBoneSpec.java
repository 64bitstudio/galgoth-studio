package com.galgothstudio.backend.domain.template;

import com.galgothstudio.backend.domain.model.Vec3;

/**
 * Especificación de un bone dentro de un {@link CanonicalTemplate} -- ticket
 * 097, HU-1. Mismas coordenadas absolutas de rest-pose que
 * {@link com.galgothstudio.backend.domain.model.Bone} (ver ADR 0001,
 * "origin"/pivot): un {@code id} lógico estable (no UUID -- se resuelve a
 * UUID real recién en {@code PrimaryGeometryGenerator}, ticket 098) referencia
 * a {@code parentId} de otro bone del mismo template, o {@code null} para la
 * raíz.
 *
 * @param id id lógico estable dentro del template (ej. {@code "left_forearm"}).
 * @param name nombre legible del bone.
 * @param parentId {@code id} de otro {@link TemplateBoneSpec} del mismo
 *                 template, o {@code null} si es la raíz.
 * @param pivot punto de pivote en rest-pose (minecraft_pixels, absoluto).
 * @param rotation rotación base en grados -- normalmente {@code (0,0,0)} en
 *                 rest-pose para un template en A-pose/T-pose editable.
 */
public record TemplateBoneSpec(String id, String name, String parentId, Vec3 pivot, Vec3 rotation) {
}
