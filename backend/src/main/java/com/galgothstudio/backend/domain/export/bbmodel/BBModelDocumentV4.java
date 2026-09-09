package com.galgothstudio.backend.domain.export.bbmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.List;

/**
 * Raíz de un `.bbmodel` v4 -- sin array `groups` separado (a diferencia de
 * {@link BBModelDocument}, v5): la jerarquía completa vive en `outliner`
 * (ver {@link BBV4OutlinerEntry}). Mismo alcance que v5 este ciclo:
 * geometría + bones, sin animación funcional.
 */
public record BBModelDocumentV4(
		BBMeta meta,
		String name,
		@JsonProperty("model_identifier") String modelIdentifier,
		@JsonProperty("visible_box") Vec3 visibleBox,
		BBResolution resolution,
		List<BBElement> elements,
		List<BBV4OutlinerEntry> outliner,
		List<BBTexture> textures,
		List<Object> animations) {
}
