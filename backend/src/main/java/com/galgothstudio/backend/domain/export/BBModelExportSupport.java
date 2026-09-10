package com.galgothstudio.backend.domain.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.galgothstudio.backend.domain.export.bbmodel.BBElement;
import com.galgothstudio.backend.domain.export.bbmodel.BBTexture;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.Base64;

/**
 * Lógica compartida entre {@link BBModelExporterV5} y {@link BBModelExporterV4}
 * -- ticket 014. Ambos exportan la MISMA geometría/jerarquía (AC #2 del
 * ticket 014); solo difieren en cómo estructuran `groups`/`outliner`.
 */
final class BBModelExportSupport {

	// UUID FIJO (no `UUID.randomUUID()`) -- ticket 044, AC de determinismo:
	// exportar el mismo modelo dos veces debe producir bytes idénticos, sin
	// dependencia de estado externo. Antes de este ticket este campo SÍ era
	// aleatorio (hallazgo real, ver Hecho del ticket 044) -- inofensivo
	// mientras el exportador recomputaba UV en cada export (ningún test
	// dependía de bytes estables), pero incompatible con la nueva garantía
	// de determinismo de `export(model)` al ser ahora el único overload.
	private static final String PLACEHOLDER_TEXTURE_UUID = "00000000-0000-4000-8000-000000000001";

	private BBModelExportSupport() {
	}

	static BBElement toElement(Cuboid cuboid) {
		Vec3 rotation = isZero(cuboid.rotation()) ? null : cuboid.rotation();
		return new BBElement(
				cuboid.name(), cuboid.id(), "cube", cuboid.from(), cuboid.to(), cuboid.origin(), rotation, 0, 0,
				cuboid.faces());
	}

	static boolean isZero(Vec3 v) {
		return v.x() == 0 && v.y() == 0 && v.z() == 0;
	}

	/**
	 * Textura placeholder embebida en cualquier export -- ticket 011, sin cambios
	 * de fondo en el ticket 044 (la reversión de ese ticket es sobre EL
	 * CÁLCULO DE LA UV, no sobre este mecanismo, ortogonal: dimensiones
	 * tomadas tal cual de {@code MobProjectModel.texture()}, nunca
	 * recalculadas). Necesaria para que Blockbench/`FmmCompatibilityValidator`
	 * resuelvan el índice de textura que ya trae cada `Face` cuando la UV
	 * fue asignada (por los llamadores de motor, nunca por el exportador).
	 */
	static BBTexture buildPlaceholderTexture(int width, int height) {
		byte[] png = PlaceholderTexture.generatePng(width, height);
		String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
		return new BBTexture(PLACEHOLDER_TEXTURE_UUID, "placeholder", "0", false, width, height, dataUri);
	}

	static String serialize(Object document) {
		try {
			ObjectMapper mapper = new ObjectMapper()
					.registerModule(new Vec3JacksonModule())
					.registerModule(new Vec4JacksonModule())
					.enable(SerializationFeature.INDENT_OUTPUT);
			return mapper.writeValueAsString(document);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("No se pudo serializar el .bbmodel", e);
		}
	}

}
