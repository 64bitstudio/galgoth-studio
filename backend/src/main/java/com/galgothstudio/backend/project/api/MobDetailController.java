package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.mob.MobService;
import com.galgothstudio.backend.project.mob.MobSummary;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ticket 034 -- ver `docs/API.md` para el contrato completo. */
@RestController
@RequestMapping("/api/mobs/{mobId}")
public class MobDetailController {

	private final MobService mobService;

	public MobDetailController(MobService mobService) {
		this.mobService = mobService;
	}

	@GetMapping
	public MobSummary get(@PathVariable UUID mobId) {
		return mobService.get(mobId);
	}

}
