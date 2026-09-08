package com.galgothstudio.backend.domain.model;

import java.util.Map;

public record AnimationEvent(double time, String type, Map<String, Object> payload) {
}
