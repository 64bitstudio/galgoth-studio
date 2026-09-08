package com.galgothstudio.backend.domain.model;

import java.util.List;

public record AnimationTrack(
		String boneId, AnimationChannel channel, Interpolation interpolation, List<Keyframe> keyframes) {
}
