package com.galgothstudio.backend.aiorchestrator.progress;

import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import java.util.List;

/**
 * Diferencia entre dos estados sucesivos del modelo en construcción
 * durante la generación (ticket 029, AC #1) -- lo que viaja en un
 * evento `preview_operations`. Deliberadamente NO son las
 * `GeometryOperation` crudas del proveedor (esas usan `tempId`s sin
 * resolver, un detalle interno del `GeometryEngine`) sino bones/cuboids
 * YA RESUELTOS con sus ids reales -- el frontend los aplica directo a
 * su modelo de preview en memoria sin tener que reimplementar la
 * resolución de referencias temporales del motor (005).
 *
 * <p>Un delta vacío ({@link #isEmpty()}) nunca se emite como evento --
 * {@link GenerationPreviewDiff#diff} puede devolverlo cuando dos
 * estados resultan idénticos (no debería ocurrir en la práctica, cada
 * operación cambia algo), y el caller lo descarta sin emitir.
 */
public record PreviewDelta(List<Bone> addedOrUpdatedBones, List<Cuboid> addedOrUpdatedCuboids, List<String> removedCuboidIds) {

	public boolean isEmpty() {
		return addedOrUpdatedBones.isEmpty() && addedOrUpdatedCuboids.isEmpty() && removedCuboidIds.isEmpty();
	}

}
