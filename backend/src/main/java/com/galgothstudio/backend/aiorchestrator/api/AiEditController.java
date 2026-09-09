package com.galgothstudio.backend.aiorchestrator.api;

import com.galgothstudio.backend.aiorchestrator.edit.AiEditService;
import com.galgothstudio.backend.aiorchestrator.edit.EditGeometryPlanView;
import com.galgothstudio.backend.aiorchestrator.edit.EditGeometryRequest;
import com.galgothstudio.backend.project.draft.ApplyGenerationResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Ticket 031 -- ver `docs/API.md` para el contrato completo. */
@RestController
public class AiEditController {

	private final AiEditService aiEditService;

	public AiEditController(AiEditService aiEditService) {
		this.aiEditService = aiEditService;
	}

	/** AC #1/#2: síncrono -- una sola llamada al `StructuredReasoningProvider`, sin SSE (a diferencia de 029). */
	@PostMapping("/api/mobs/{mobId}/ai/edit-geometry")
	public EditGeometryPlanView requestPlan(@PathVariable UUID mobId, @RequestBody EditGeometryRequest request) {
		return aiEditService.requestPlan(mobId, request.instruction());
	}

	/** "Aplicar cambios" (mockup 06) -- AC #3/#4/#5. */
	@PostMapping("/api/jobs/{jobId}/apply-edit")
	public ResponseEntity<ApplyGenerationResponse> applyEdit(@PathVariable UUID jobId) {
		return ResponseEntity.status(201).body(aiEditService.applyEdit(jobId));
	}

}
