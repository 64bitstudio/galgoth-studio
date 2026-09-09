package com.galgothstudio.backend.aiorchestrator.progress;

import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Función pura: diferencia dos {@link MobProjectModel} sucesivos (antes/
 * después de aplicar UNA operación más del batch, ticket 029). Genérica
 * a propósito sobre las 9 operaciones de la whitelist (005) -- en vez de
 * interpretar cada tipo de operación por separado, compara los bones/
 * cuboids resultantes por id, así que `resizeCuboid`/`moveCuboid`/
 * `rotateCuboid`/`setBonePivot`/`setBoneRotation`/`parentBone` quedan
 * cubiertas como "actualizado" con el mismo código que `createBone`/
 * `createCuboid` como "agregado" -- nunca diverge de lo que el
 * `GeometryEngine` (única autoridad real) produjo.
 */
public final class GenerationPreviewDiff {

	private GenerationPreviewDiff() {
	}

	public static PreviewDelta diff(MobProjectModel before, MobProjectModel after) {
		List<Bone> changedBones = changed(before.bones(), after.bones(), Bone::id);
		List<Cuboid> changedCuboids = changed(before.cuboids(), after.cuboids(), Cuboid::id);
		List<String> removedCuboidIds = removedIds(before.cuboids(), after.cuboids(), Cuboid::id);
		return new PreviewDelta(changedBones, changedCuboids, removedCuboidIds);
	}

	private static <T> List<T> changed(List<T> before, List<T> after, Function<T, String> idOf) {
		Map<String, T> beforeById = before.stream().collect(Collectors.toMap(idOf, item -> item));
		List<T> result = new ArrayList<>();
		for (T item : after) {
			T previous = beforeById.get(idOf.apply(item));
			if (previous == null || !previous.equals(item)) {
				result.add(item);
			}
		}
		return result;
	}

	private static <T> List<String> removedIds(List<T> before, List<T> after, Function<T, String> idOf) {
		Set<String> afterIds = after.stream().map(idOf).collect(Collectors.toSet());
		List<String> result = new ArrayList<>();
		for (T item : before) {
			String id = idOf.apply(item);
			if (!afterIds.contains(id)) {
				result.add(id);
			}
		}
		return result;
	}

}
