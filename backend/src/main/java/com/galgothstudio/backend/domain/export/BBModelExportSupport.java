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
	//
	// Ticket 056: el MISMO UUID/slot se reutiliza tanto para la textura
	// placeholder como para la textura real -- ambas ocupan el ÚNICO
	// índice de textura (0) que `Face.texture` referencia (ver docstring
	// de `BBTexture`: la POSICIÓN en el array es lo que importa, nunca el
	// campo `id`), así que no hace falta un UUID distinto por caso.
	private static final String TEXTURE_UUID = "00000000-0000-4000-8000-000000000001";

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
	 * Textura embebida en cualquier export -- ticket 011 (placeholder),
	 * ampliado en el ticket 056 (HU-43) para embeber la textura REAL
	 * cuando existe: {@code realPngBytes} llega ya resuelto por el CALLER
	 * (`MobExportService`, vía `AssetStorageService`) -- el exportador
	 * sigue sin ninguna dependencia de infraestructura/I/O (garantía ya
	 * defendida por el PO, "Hallazgo A revertido" del Diseño técnico §3 de
	 * `docs/definiciones/galgoth-studio-fase3-textura.md"): recibe bytes ya
	 * en memoria, nunca una `AssetStorageService`/key que resolver él
	 * mismo. `null` (mob sin ninguna región pintada, o legacy sin
	 * `storageKey`) preserva el comportamiento de siempre: checkerboard
	 * auto-generado, dimensiones EXACTAS al atlas del modelo.
	 *
	 * <p>Necesaria en ambos casos para que Blockbench/`FmmCompatibilityValidator`
	 * resuelvan el índice de textura que ya trae cada `Face` cuando la UV
	 * fue asignada (por los llamadores de motor, nunca por el exportador).
	 */
	static BBTexture buildTexture(int width, int height, byte[] realPngBytes) {
		boolean hasRealTexture = realPngBytes != null;
		byte[] png = hasRealTexture ? realPngBytes : PlaceholderTexture.generatePng(width, height);
		String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
		String name = hasRealTexture ? "texture" : "placeholder";
		return new BBTexture(TEXTURE_UUID, name, "0", false, width, height, dataUri);
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
