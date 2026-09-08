package com.galgothstudio.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum BaseType {
	@JsonProperty("humanoid") HUMANOID,
	@JsonProperty("arachnid") ARACHNID,
	@JsonProperty("quadruped") QUADRUPED,
	@JsonProperty("flying") FLYING,
	@JsonProperty("custom") CUSTOM

}
