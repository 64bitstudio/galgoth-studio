package com.galgothstudio.backend.domain.export.bbmodel;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.Vec3;

/**
 * Cuboid del formato `.bbmodel` -- reutiliza {@link CuboidFaces} directamente
 * (mismas claves north/south/east/west/up/down, mismo shape {@code {uv,texture}}
 * que espera Blockbench, ver {@link com.galgothstudio.backend.domain.model.Face}).
 *
 * @param rotation {@code null} cuando es [0,0,0] -- Blockbench omite el campo
 *                 por completo en ese caso (verificado en {@code Cube.getSaveCopy()}
 *                 del código fuente real: {@code if (!this.rotation.allEqual(0)) el.rotation = this.rotation}),
 *                 no lo escribe como `[0,0,0]` explícito.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BBElement(
		String name,
		String uuid,
		String type,
		Vec3 from,
		Vec3 to,
		Vec3 origin,
		Vec3 rotation,
		int color,
		int autouv,
		CuboidFaces faces) {
}
