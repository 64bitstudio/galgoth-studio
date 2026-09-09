package com.galgothstudio.backend.project.mob;

import java.time.Instant;

/** Una tarjeta del grid de mobs del detalle de proyecto (HU-04). */
public record MobSummary(String id, String name, String baseType, String status, String thumbnailKey, Instant updatedAt) {
}
