package com.galgothstudio.backend.domain.export;

import com.galgothstudio.backend.domain.export.bbmodel.BBElement;
import com.galgothstudio.backend.domain.export.bbmodel.BBMeta;
import com.galgothstudio.backend.domain.export.bbmodel.BBModelDocumentV4;
import com.galgothstudio.backend.domain.export.bbmodel.BBResolution;
import com.galgothstudio.backend.domain.export.bbmodel.BBTexture;
import com.galgothstudio.backend.domain.export.bbmodel.BBV4OutlinerEntry;
import com.galgothstudio.backend.domain.export.bbmodel.BBV4OutlinerGroup;
import com.galgothstudio.backend.domain.export.bbmodel.BBV4OutlinerLeaf;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exportador `.bbmodel` versión 4 (compatibilidad) -- ticket 014, épica
 * 009. No bloquea el Gate M1 (que solo exige v5), pero es requisito del
 * Gate M6 (cierre del Technical Alpha). Exporta la MISMA geometría/
 * jerarquía que {@link BBModelExporterV5} para el mismo `MobProjectModel`
 * (AC #2 del ticket) -- comparten helpers vía {@link BBModelExportSupport}
 * y solo difieren en cómo estructuran `groups`/`outliner`: v4 no separa
 * `groups` (formato pre-Blockbench-5.0) -- cada group embebe sus datos
 * completos directo dentro del árbol `outliner` (ver {@link BBV4OutlinerGroup}).
 *
 * <p>{@code format_version: "4.10"} y la ausencia de la clave `groups` se
 * verificaron contra un `.bbmodel` real del proyecto
 * (`samples/carcomido_minecraft_cuboids.bbmodel`), no inventados --
 * mismo criterio que se usó para derivar el formato v5 real en el ticket 010.
 */
public final class BBModelExporterV4 {

	private static final String FORMAT_VERSION = "4.10";
	private static final String MODEL_FORMAT = "free";
	private static final Vec3 DEFAULT_VISIBLE_BOX = new Vec3(8, 8, 8);
	private static final String ROOT_KEY = "";

	private BBModelExporterV4() {
	}

	/** Ver {@link BBModelExporterV5#export(MobProjectModel)} -- misma semántica (ticket 044: nunca recomputa UV). */
	public static String export(MobProjectModel model) {
		BBTexture placeholder =
				BBModelExportSupport.buildPlaceholderTexture(model.texture().width(), model.texture().height());
		return BBModelExportSupport.serialize(buildDocument(model, List.of(placeholder)));
	}

	private static BBModelDocumentV4 buildDocument(MobProjectModel model, List<BBTexture> textures) {
		List<BBElement> elements = model.cuboids().stream().map(BBModelExportSupport::toElement).toList();
		List<BBV4OutlinerEntry> outliner = buildOutliner(model);

		return new BBModelDocumentV4(
				new BBMeta(FORMAT_VERSION, MODEL_FORMAT, false),
				model.name(),
				model.mobId(),
				DEFAULT_VISIBLE_BOX,
				new BBResolution(model.texture().width(), model.texture().height()),
				elements,
				outliner,
				List.copyOf(textures),
				List.of());
	}

	private static List<BBV4OutlinerEntry> buildOutliner(MobProjectModel model) {
		Map<String, List<Bone>> bonesByParent = model.bones()
				.stream()
				.collect(Collectors.groupingBy(bone -> bone.parentId() == null ? ROOT_KEY : bone.parentId()));
		Map<String, List<Cuboid>> cuboidsByBone = model.cuboids().stream().collect(Collectors.groupingBy(Cuboid::boneId));

		return buildOutlinerLevel(ROOT_KEY, bonesByParent, cuboidsByBone);
	}

	private static List<BBV4OutlinerEntry> buildOutlinerLevel(
			String parentKey, Map<String, List<Bone>> bonesByParent, Map<String, List<Cuboid>> cuboidsByBone) {
		List<BBV4OutlinerEntry> entries = new ArrayList<>();
		for (Bone bone : bonesByParent.getOrDefault(parentKey, List.of())) {
			List<BBV4OutlinerEntry> children = new ArrayList<>();
			for (Cuboid cuboid : cuboidsByBone.getOrDefault(bone.id(), List.of())) {
				children.add(new BBV4OutlinerLeaf(cuboid.id()));
			}
			children.addAll(buildOutlinerLevel(bone.id(), bonesByParent, cuboidsByBone));
			Vec3 rotation = BBModelExportSupport.isZero(bone.rotation()) ? null : bone.rotation();
			entries.add(new BBV4OutlinerGroup(bone.name(), bone.id(), bone.pivot(), rotation, true, true, children));
		}
		return entries;
	}

}
