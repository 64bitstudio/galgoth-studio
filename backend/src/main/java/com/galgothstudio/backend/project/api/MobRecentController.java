package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.mob.MobService;
import com.galgothstudio.backend.project.mob.RecentMobSummary;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket 071 -- "Continuar trabajando" (Inicio, rediseño de la pantalla de
 * inicio). Ruta propia `/api/mobs/recent` (no `/api/mobs/{mobId}`, que ya
 * existe en {@link MobDetailController}) -- Spring resuelve el segmento
 * literal "recent" antes que el path-variable de ese otro controller, sin
 * ambigüedad real entre ambos.
 */
@RestController
@RequestMapping("/api/mobs/recent")
public class MobRecentController {

	private static final int DEFAULT_LIMIT = 3;
	private static final int MAX_LIMIT = 20;

	private final MobService mobService;

	public MobRecentController(MobService mobService) {
		this.mobService = mobService;
	}

	@GetMapping
	public List<RecentMobSummary> list(@RequestParam(required = false) Integer limit) {
		return mobService.listRecentAcrossProjects(clamp(limit));
	}

	/** `limit` es un detalle de presentación (cuántas cards caben en la fila de Inicio), no un parámetro de negocio -- un valor inválido/ausente cae a un default sensato en vez de un 400. */
	private int clamp(Integer limit) {
		if (limit == null || limit < 1) {
			return DEFAULT_LIMIT;
		}
		return Math.min(limit, MAX_LIMIT);
	}

}
