package com.galgothstudio.backend.domain.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.galgothstudio.backend.domain.export.bbmodel.BBElement;
import com.galgothstudio.backend.domain.export.bbmodel.BBTexture;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.uv.UvLayoutStrategy;
import java.util.Base64;
import java.util.UUID;

/**
 * Lógica compartida entre {@link BBModelExporterV5} y {@link BBModelExporterV4}
 * -- ticket 014. Ambos exportan la MISMA geometría/jerarquía (AC #2 del
 * ticket 014); solo difieren en cómo estructuran `groups`/`outliner`.
 */
final class BBModelExportSupport {

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

	static BBTexture buildPlaceholderTexture(int width, int height) {
		byte[] png = PlaceholderTexture.generatePng(width, height);
		String dataUri = "data:image/png;base64," + Base64.getEncoder().encodeToString(png);
		return new BBTexture(UUID.randomUUID().toString(), "placeholder", "0", false, width, height, dataUri);
	}

	/** Recomputa la UV vía {@code uvLayoutStrategy} y devuelve un modelo con esa UV fresca -- ver export(model, strategy) de cada exportador. */
	static MobProjectModel withFreshUv(MobProjectModel model, UvLayoutStrategy uvLayoutStrategy) {
		int width = model.texture().width();
		int height = model.texture().height();
		UvLayoutStrategy.Result uvResult = uvLayoutStrategy.layout(model.cuboids(), width, height);
		return new MobProjectModel(
				model.mobId(), model.projectId(), model.name(), model.baseType(), model.units(), model.bones(),
				uvResult.cuboids(), model.texture(), new UvLayout(width, height, uvResult.regions()),
				model.animations(), model.exportSettings(), model.referenceImages());
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
