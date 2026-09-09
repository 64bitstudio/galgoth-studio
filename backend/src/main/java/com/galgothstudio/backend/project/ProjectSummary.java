package com.galgothstudio.backend.project;

import java.time.Instant;
import java.util.List;

/** Una tarjeta del dashboard "Mis proyectos" (HU-02). `mobThumbnails` trae como máximo 3 -- `mobCount` es el total real, para el indicador "+N". */
public record ProjectSummary(
		String id, String name, int mobCount, List<MobThumbnail> mobThumbnails, Instant createdAt, Instant updatedAt) {
}
