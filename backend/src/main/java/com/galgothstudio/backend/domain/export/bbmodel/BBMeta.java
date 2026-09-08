package com.galgothstudio.backend.domain.export.bbmodel;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bloque {@code meta} del formato `.bbmodel`. {@code formatVersion} decide
 * el formato mayor: Blockbench 5.0 introdujo la separación `groups`/`outliner`
 * (verificado contra el código fuente real de Blockbench, `js/formats/bbmodel.js`,
 * constante {@code FORMATV = '5.0'} -- no inventado, ver Hecho del ticket 010).
 */
public record BBMeta(
		@JsonProperty("format_version") String formatVersion,
		@JsonProperty("model_format") String modelFormat,
		@JsonProperty("box_uv") boolean boxUv) {
}
