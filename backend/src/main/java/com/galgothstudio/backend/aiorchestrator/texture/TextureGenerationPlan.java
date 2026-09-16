package com.galgothstudio.backend.aiorchestrator.texture;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.FaceMaterialNote;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TexturePlan;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Une, por {@code (cuboidId, face)}, todo lo que
 * {@link TextureSheetPromptComposer} necesita para escribir una línea de
 * prompt autosuficiente -- ticket 101 (HU-7 de
 * `docs/definiciones/anatomia-por-capas-generacion-mobs.md`). Antes de
 * este ticket, esa información vivía repartida: {@code semanticPart} en
 * {@link Cuboid} (099, persistido, granularidad de cuboid), la nota de
 * material en {@link FaceMaterialNote} (052, granularidad de BONE+cara,
 * no de cuboid+cara) y la región de atlas en
 * {@link CuboidFacePlacement#atlasUvRect()} (053) -- ningún tipo único
 * las juntaba, así que {@code TextureSheetPromptComposer} solo podía
 * mostrar un bloque de notas compartido al inicio del prompt, nunca una
 * nota adjunta a su propia línea de coordenadas.
 *
 * <p><b>Ancla a {@code semanticPart} PERSISTIDO, nunca reinferido</b>: a
 * diferencia de {@link com.galgothstudio.backend.domain.model.BoneSemanticLabel}
 * (que el {@code VisionModelProvider} infiere de nuevo en cada corrida a
 * partir del nombre libre del bone), este plan lee
 * {@link Cuboid#semanticPart()} tal cual quedó grabado por el pipeline de
 * geometría (097-099) -- la misma fuente de verdad que
 * {@code SemanticPartCategory} (ticket 104) va a cerrar más adelante.
 *
 * <p>100% determinista, sin IA, sin estado -- se construye de nuevo por
 * cada {@link TextureGenerationSheet} directamente a partir de sus propios
 * {@code placements()} ({@link #forSheet}), así que {@link #find} SIEMPRE
 * encuentra una entrada para cualquier {@code (cuboidId, face)} que
 * provenga de esa misma sheet (invariante 1:1, ver su Javadoc).
 *
 * <p>Deliberadamente NO se agregan campos a {@link TextureGenerationSheet}
 * ni a {@link CuboidFacePlacement} para esto (ambos "forma EXACTA fijada",
 * ticket 053) -- este plan es un tipo aparte, construido a partir de
 * ellos, no una extensión de su forma congelada.
 */
public record TextureGenerationPlan(List<Entry> entries) {

	/**
	 * @param cuboidId     id real del {@link Cuboid} dueño de esta cara.
	 * @param face         cara del cuboid.
	 * @param semanticPart {@link Cuboid#semanticPart()} persistido -- {@code null} si el cuboid no lo tiene (cuboids anteriores al ticket 099, o creados por un camino que no lo asigna).
	 * @param materialNote nota de {@link FaceMaterialNote} para el {@code (boneId, face)} de este placement -- {@code null} si el {@code TexturePlan} no trae ninguna para esa combinación.
	 * @param atlasRegion  {@link CuboidFacePlacement#atlasUvRect()} de esta misma cara -- mismo footprint real del atlas, leído una sola vez.
	 */
	public record Entry(String cuboidId, FaceName face, String semanticPart, String materialNote, Vec4 atlasRegion) {
	}

	/**
	 * Arma el plan para exactamente los {@code placements()} de {@code sheet}
	 * -- una entrada por placement, en el mismo orden. {@code texturePlan}
	 * es el mismo ya calculado por {@code TexturePlanService} (052) para
	 * este job; {@code model} es el modelo actual del draft (fuente de
	 * {@code Cuboid.semanticPart()}).
	 */
	public static TextureGenerationPlan forSheet(MobProjectModel model, TexturePlan texturePlan, TextureGenerationSheet sheet) {
		Map<String, String> semanticPartByCuboidId = semanticPartByCuboidId(model);
		List<Entry> entries = new ArrayList<>(sheet.placements().size());
		for (CuboidFacePlacement placement : sheet.placements()) {
			String semanticPart = semanticPartByCuboidId.get(placement.cuboidId());
			String materialNote = materialNoteFor(texturePlan, sheet.boneId(), placement.face());
			entries.add(new Entry(placement.cuboidId(), placement.face(), semanticPart, materialNote, placement.atlasUvRect()));
		}
		return new TextureGenerationPlan(entries);
	}

	public Optional<Entry> find(String cuboidId, FaceName face) {
		for (Entry entry : entries) {
			if (entry.cuboidId().equals(cuboidId) && entry.face() == face) {
				return Optional.of(entry);
			}
		}
		return Optional.empty();
	}

	private static Map<String, String> semanticPartByCuboidId(MobProjectModel model) {
		Map<String, String> result = new HashMap<>();
		for (Cuboid cuboid : model.cuboids()) {
			result.put(cuboid.id(), cuboid.semanticPart());
		}
		return result;
	}

	private static String materialNoteFor(TexturePlan texturePlan, String boneId, FaceName face) {
		for (FaceMaterialNote note : texturePlan.materialNotes()) {
			if (note.boneId().equals(boneId) && note.face() == face) {
				return note.note();
			}
		}
		return null;
	}

}
