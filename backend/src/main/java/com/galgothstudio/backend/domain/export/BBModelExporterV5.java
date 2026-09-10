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
	 * Único export -- ticket 044, Diseño técnico §3 de
	 * `docs/definiciones/galgoth-studio-fase3-textura.md` (reversión directa
	 * de una decisión previa, ticket 011: el exportador SÍ recomputaba UV
	 * en cada export). Nunca invoca {@code UvLayoutSelector}/
	 * {@code AlphaAutoPackStrategy}/{@code StableUvStrategy} -- de hecho no
	 * depende en absoluto de {@code domain.uv} (garantía estructural, no
	 * solo de comportamiento). Nunca muta el {@code UvLayout}/{@code faces}
	 * que trae {@code model}: serializa EXACTAMENTE {@code model.uv()} y
	 * {@code model.texture()} tal como llegan -- la UV canónica de esa
	 * Revision, decidida en su momento por quien aplicó la última operación
	 * de geometría, nunca en este punto. Determinista: el mismo modelo
	 * exportado dos veces produce bytes idénticos.
	 *
	 * <p>Embebe la textura PLACEHOLDER (dimensiones EXACTAS al atlas,
	 * `MobProjectModel.texture`, ticket 011) -- mecanismo ortogonal a la UV
	 * en sí (Blockbench/`FmmCompatibilityValidator` necesitan poder
	 * resolver el índice de textura que ya trae cada {@code Face} cuando la
	 * UV fue asignada, sin importar quién la asignó). Overload de
	 * conveniencia -- ver {@link #export(MobProjectModel, byte[])} (ticket
	 * 056, HU-43) para embeber la textura REAL en vez del placeholder.
	 *
	 * <p>Para revisiones legacy de Fase 1+2 que necesiten normalizar su UV
	 * antes de este punto, ver {@code LegacyUvNormalizationService}
	 * (`domain/uv/`), invocado por el caller ANTES de este método -- nunca
	 * dentro de él.
	 */
	public static String export(MobProjectModel model) {
		return export(model, null);
	}

	/**
	 * Ticket 056 (HU-43) -- variante que embebe la textura REAL pintada/
	 * generada en vez del checkerboard placeholder, cuando {@code
	 * realTexturePngBytes} no es {@code null}. El exportador SIGUE siendo
	 * un serializador puro (garantía ya defendida por el PO, "Hallazgo A
	 * revertido", Diseño técnico §3 de
	 * `docs/definiciones/galgoth-studio-fase3-textura.md`): nunca resuelve
	 * él mismo un {@code storageKey} ni conoce `AssetStorageService` -- el
	 * CALLER (`MobExportService`) ya resolvió los bytes reales (o decidió
	 * pasar {@code null}) antes de invocar este método, mismo patrón que
	 * `LegacyUvNormalizationService` para la UV legacy. {@code null}
	 * preserva EXACTAMENTE el comportamiento de siempre (placeholder).
	 */
	public static String export(MobProjectModel model, byte[] realTexturePngBytes) {
		BBTexture texture =
				BBModelExportSupport.buildTexture(model.texture().width(), model.texture().height(), realTexturePngBytes);
		return BBModelExportSupport.serialize(buildDocument(model, List.of(texture)));
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
