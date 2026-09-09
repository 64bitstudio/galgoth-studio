package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.mob.CreateMobRequest;
import com.galgothstudio.backend.project.mob.MobService;
import com.galgothstudio.backend.project.mob.MobSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ticket 022 (HU-03/HU-04) -- ver `docs/API.md` para el contrato completo. */
@RestController
@RequestMapping("/api/projects/{projectId}/mobs")
public class MobController {

	private final MobService mobService;

	public MobController(MobService mobService) {
		this.mobService = mobService;
	}

	@PostMapping
	public ResponseEntity<MobSummary> create(@PathVariable UUID projectId, @RequestBody CreateMobRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(mobService.create(projectId, request.name(), request.baseType()));
	}

	@GetMapping
	public List<MobSummary> list(@PathVariable UUID projectId) {
		return mobService.list(projectId);
	}

}
