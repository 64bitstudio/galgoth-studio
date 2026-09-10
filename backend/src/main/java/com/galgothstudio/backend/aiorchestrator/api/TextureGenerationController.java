package com.galgothstudio.backend.aiorchestrator.api;

import com.galgothstudio.backend.aiorchestrator.texture.GenerateTextureRequest;
import com.galgothstudio.backend.aiorchestrator.texture.TextureGenerationResultView;
import com.galgothstudio.backend.aiorchestrator.texture.TextureGenerationService;
import com.galgothstudio.backend.project.draft.ApplyGenerationResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket 054 (HU-36 a HU-39) -- ver `docs/API.md`. El progreso SSE
 * reutiliza `GET /api/jobs/{jobId}/events` tal cual (029) -- ese endpoint
 * es genérico por `jobId`, sin lógica específica de `job_type`, así que
 * no hace falta tocar {@link GenerationJobController}.
 */
@RestController
public class TextureGenerationController {

	private final TextureGenerationService textureGenerationService;

	public TextureGenerationController(TextureGenerationService textureGenerationService) {
		this.textureGenerationService = textureGenerationService;
	}

	/** HU-36 (`boneId` ausente/null -&gt; el modelo completo) / HU-37 (`boneId` presente -&gt; un bone puntual). */
	@PostMapping("/api/mobs/{mobId}/ai/generate-texture")
	public ResponseEntity<StartGenerationResponse> startGeneration(@PathVariable UUID mobId, @RequestBody GenerateTextureRequest request) {
		UUID jobId = textureGenerationService.startGeneration(mobId, request);
		return ResponseEntity.accepted().body(new StartGenerationResponse(jobId));
	}

	/** HU-38 -- diff Antes/Después de una propuesta ya completada. */
	@GetMapping("/api/jobs/{jobId}/texture-result")
	public TextureGenerationResultView result(@PathVariable UUID jobId) {
		return textureGenerationService.getResult(jobId);
	}

	/** HU-38 AC #3 -- Apply atómico con chequeo de conflicto 409 (Diseño técnico §10/§16). */
	@PostMapping("/api/jobs/{jobId}/apply-texture")
	public ResponseEntity<ApplyGenerationResponse> apply(@PathVariable UUID jobId) {
		return ResponseEntity.status(201).body(textureGenerationService.applyTexture(jobId));
	}

}
