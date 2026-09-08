package com.galgothstudio.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum FaceName {
	@JsonProperty("north") NORTH,
	@JsonProperty("south") SOUTH,
	@JsonProperty("east") EAST,
	@JsonProperty("west") WEST,
	@JsonProperty("up") UP,
	@JsonProperty("down") DOWN

}
