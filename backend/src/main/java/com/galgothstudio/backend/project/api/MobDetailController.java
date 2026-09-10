package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.mob.MobService;
import com.galgothstudio.backend.project.mob.MobSummary;
import com.galgothstudio.backend.project.mob.RenameMobRequest;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket 034 -- ver `docs/API.md` para el contrato completo. Ticket 039
 * agrega Renombrar/Eliminar (`PATCH`/`DELETE`) -- mismo criterio de
 * identificar el mob solo por `mobId` (sin `projectId` en el path) que
 * ya usaba `GET`.
 */
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

	@PatchMapping
	public MobSummary rename(@PathVariable UUID mobId, @RequestBody RenameMobRequest request) {
		return mobService.rename(mobId, request.name());
	}

	@DeleteMapping
	public ResponseEntity<Void> delete(@PathVariable UUID mobId) {
		mobService.softDelete(mobId);
		return ResponseEntity.noContent().build();
	}

}
