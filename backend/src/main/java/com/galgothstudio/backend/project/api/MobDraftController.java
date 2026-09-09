package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.draft.AutosaveRequest;
import com.galgothstudio.backend.project.draft.AutosaveResponse;
import com.galgothstudio.backend.project.draft.DraftPersistenceService;
import com.galgothstudio.backend.project.draft.DraftView;
import com.galgothstudio.backend.project.draft.SaveRevisionRequest;
import com.galgothstudio.backend.project.draft.SaveRevisionResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ticket 020 -- ver `docs/API.md` para el contrato completo de cada endpoint. */
@RestController
@RequestMapping("/api/mobs/{mobId}")
public class MobDraftController {

	private final DraftPersistenceService draftPersistenceService;

	public MobDraftController(DraftPersistenceService draftPersistenceService) {
		this.draftPersistenceService = draftPersistenceService;
	}

	@GetMapping("/draft")
	public DraftView getDraft(@PathVariable UUID mobId) {
		return draftPersistenceService.getDraft(mobId);
	}

	@PatchMapping("/draft")
	public AutosaveResponse autosave(@PathVariable UUID mobId, @RequestBody AutosaveRequest request) {
		return draftPersistenceService.autosave(mobId, request.model());
	}

	@PostMapping("/revisions")
	public ResponseEntity<SaveRevisionResponse> saveRevision(@PathVariable UUID mobId, @RequestBody SaveRevisionRequest request) {
		SaveRevisionResponse response = draftPersistenceService.saveRevision(mobId, request.model());
		HttpStatus status = response.created() ? HttpStatus.CREATED : HttpStatus.OK;
		return ResponseEntity.status(status).body(response);
	}

}
