package com.galgothstudio.backend.domain.export.bbmodel;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.List;

/**
 * Raíz de un archivo `.bbmodel`. Este ciclo cubre exclusivamente geometría +
 * bones (ticket 010, sin textura/animación funcional -- ver 011 para
 * textura placeholder): {@code textures}/{@code animations} quedan vacíos.
 * Campos opcionales del formato real (`variable_placeholders`, `display`,
 * `backgrounds`, `history`) se omiten deliberadamente -- Blockbench los
 * trata como opcionales al cargar (cada uso está guardado detrás de un
 * `if (model.xxx)` en el código fuente real), su ausencia no dispara el
 * diálogo de reparación.
 */
public record BBModelDocument(
		BBMeta meta,
		String name,
		@JsonProperty("model_identifier") String modelIdentifier,
		@JsonProperty("visible_box") Vec3 visibleBox,
		BBResolution resolution,
		List<BBElement> elements,
		List<BBOutlinerEntry> outliner,
		List<BBGroup> groups,
		List<Object> textures,
		List<Object> animations) {
}
