package com.galgothstudio.backend.domain.model;

import java.util.List;

/**
 * Sin generador/timeline funcional este ciclo (Fase 4) -- presente en el
 * esquema por diseño (AC #5). Forma fiel a
 * samples/animation_spec_example.json.
 */
public record AnimationSpec(
		String id,
		String name,
		double duration,
		boolean loop,
		String category,
		AnimationStyle style,
		List<AnimationTrack> tracks,
		List<AnimationEvent> events) {
}
