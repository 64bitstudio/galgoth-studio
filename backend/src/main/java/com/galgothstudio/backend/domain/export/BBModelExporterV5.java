package com.galgothstudio.backend.domain.export;

import com.galgothstudio.backend.domain.export.bbmodel.BBElement;
import com.galgothstudio.backend.domain.export.bbmodel.BBGroup;
import com.galgothstudio.backend.domain.export.bbmodel.BBMeta;
import com.galgothstudio.backend.domain.export.bbmodel.BBModelDocument;
import com.galgothstudio.backend.domain.export.bbmodel.BBOutlinerEntry;
import com.galgothstudio.backend.domain.export.bbmodel.BBOutlinerGroupRef;
import com.galgothstudio.backend.domain.export.bbmodel.BBOutlinerLeaf;
import com.galgothstudio.backend.domain.export.bbmodel.BBResolution;
import com.galgothstudio.backend.domain.export.bbmodel.BBTexture;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.uv.UvLayoutStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exportador `.bbmodel` versión 5 (separación `groups`/`outliner` de
 * Blockbench 5.0) -- ticket 010, épica 009. Opera exclusivamente sobre
 * geometría + bones de un {@link MobProjectModel}; sin animación funcional
 * este ciclo. Ver {@link BBModelExporterV4} para la variante de
 * compatibilidad (ticket 014) -- ambos exportan la MISMA geometría/
 * jerarquía, comparten helpers vía {@link BBModelExportSupport}, y solo
 * difieren en cómo estructuran `groups`/`outliner`.
 *
 * <p>Mapeo `MobProjectModel` -&gt; `.bbmodel` -- copia DIRECTA de campos
 * (sin transformación adicional), per `docs/adr/0001-coordinate-system-contract.md`
 * ("Mapeo Three.js ↔ MobProjectModel ↔ .bbmodel": `origin: [x,y,z]`,
 * `rotation: [x,y,z]`, mismas unidades/ejes en ambos lados por diseño del
 * contrato, no algo que este exportador decida de nuevo). El orden exacto
 * de composición de rotación queda documentado en el ADR como "riesgo
 * abierto de bajo impacto" hasta que el ticket 012 lo valide contra
 * fixtures reales de Blockbench -- este exportador no lo resuelve, solo
 * aplica la convención ya definida en 004.
 */
public final class BBModelExporterV5 {

	private static final String FORMAT_VERSION = "5.0";
	private static final String MODEL_FORMAT = "free";
	// Bounding box de referencia visual en el editor de Blockbench -- no
	// afecta geometría/exportación funcional, valor por defecto fijo (no
	// derivado del modelo, ninguna AC de este ticket depende de él).
	private static final Vec3 DEFAULT_VISIBLE_BOX = new Vec3(8, 8, 8);
	// Sentinel para agrupar bones raíz (parentId == null) en el mismo mapa
	// que los bones con padre real -- ningún bone real usa "" como id (UUID).
	private static final String ROOT_KEY = "";

	private BBModelExporterV5() {
	}

	/**
	 * Export sin recomputar UV -- para callers que ya garantizan una UV
	 * fresca/válida (o que deliberadamente no la necesitan, ej. tests de
	 * estructura pura). Sin textura embebida (ticket 010, alcance original).
	 */
	public static String export(MobProjectModel model) {
		return BBModelExportSupport.serialize(buildDocument(model, List.of()));
	}

	/**
	 * Export completo (ticket 011): SIEMPRE recomputa la UV vía
	 * {@code uvLayoutStrategy} antes de exportar -- backend es la autoridad
	 * canónica de UV también en el punto de export
	 * (`docs/definiciones/galgoth-studio-mvp.md` Diseño técnico §6, HU-16:
	 * "...o se exporta, cuando esas acciones corren en backend, entonces
	 * AutoUv del backend recomputa/revalida la UV de forma canónica").
	 * Genera y embebe una textura placeholder con las MISMAS dimensiones
	 * que el atlas (`MobProjectModel.texture`). Si el modelo no cabe en el
	 * atlas, {@code uvLayoutStrategy.layout(...)} lanza
	 * {@link com.galgothstudio.backend.domain.uv.UvAtlasOverflowException}
	 * -- se propaga tal cual, el export falla explícito, nunca genera una
	 * textura más grande en silencio (AC #3).
	 */
	public static String export(MobProjectModel model, UvLayoutStrategy uvLayoutStrategy) {
		MobProjectModel modelWithFreshUv = BBModelExportSupport.withFreshUv(model, uvLayoutStrategy);
		BBTexture placeholder =
				BBModelExportSupport.buildPlaceholderTexture(model.texture().width(), model.texture().height());
		return BBModelExportSupport.serialize(buildDocument(modelWithFreshUv, List.of(placeholder)));
	}

	private static BBModelDocument buildDocument(MobProjectModel model, List<BBTexture> textures) {
		List<BBElement> elements = model.cuboids().stream().map(BBModelExportSupport::toElement).toList();
		List<BBGroup> groups = model.bones().stream().map(BBModelExporterV5::toGroup).toList();
		List<BBOutlinerEntry> outliner = buildOutliner(model);

		return new BBModelDocument(
				new BBMeta(FORMAT_VERSION, MODEL_FORMAT, false),
				model.name(),
				model.mobId(),
				DEFAULT_VISIBLE_BOX,
				new BBResolution(model.texture().width(), model.texture().height()),
				elements,
				outliner,
				groups,
				List.copyOf(textures),
				List.of());
	}

	private static BBGroup toGroup(Bone bone) {
		return new BBGroup(bone.name(), bone.id(), bone.pivot(), bone.rotation(), true, true);
	}

	private static List<BBOutlinerEntry> buildOutliner(MobProjectModel model) {
		Map<String, List<Bone>> bonesByParent = model.bones()
				.stream()
				.collect(Collectors.groupingBy(bone -> bone.parentId() == null ? ROOT_KEY : bone.parentId()));
		Map<String, List<Cuboid>> cuboidsByBone = model.cuboids().stream().collect(Collectors.groupingBy(Cuboid::boneId));

		return buildOutlinerLevel(ROOT_KEY, bonesByParent, cuboidsByBone);
	}

	private static List<BBOutlinerEntry> buildOutlinerLevel(
			String parentKey, Map<String, List<Bone>> bonesByParent, Map<String, List<Cuboid>> cuboidsByBone) {
		List<BBOutlinerEntry> entries = new ArrayList<>();
		for (Bone bone : bonesByParent.getOrDefault(parentKey, List.of())) {
			List<BBOutlinerEntry> children = new ArrayList<>();
			for (Cuboid cuboid : cuboidsByBone.getOrDefault(bone.id(), List.of())) {
				children.add(new BBOutlinerLeaf(cuboid.id()));
			}
			children.addAll(buildOutlinerLevel(bone.id(), bonesByParent, cuboidsByBone));
			entries.add(new BBOutlinerGroupRef(bone.id(), true, children));
		}
		return entries;
	}

}
