package com.galgothstudio.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AnimationChannel {
	@JsonProperty("rotation") ROTATION,
	@JsonProperty("position") POSITION,
	@JsonProperty("scale") SCALE

}
