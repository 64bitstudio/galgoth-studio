package com.galgothstudio.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Interpolation {
	@JsonProperty("linear") LINEAR,
	@JsonProperty("step") STEP,
	@JsonProperty("catmullrom") CATMULLROM

}
