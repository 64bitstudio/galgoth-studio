package com.galgothstudio.backend.aiorchestrator.api;

import com.galgothstudio.backend.aiorchestrator.JobNotFoundException;
import com.galgothstudio.backend.aiorchestrator.MobGenerationService;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventRepository;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationEventBroadcaster;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Ticket 029 -- ver `docs/API.md` para el contrato completo de cada endpoint. */
@RestController
public class GenerationJobController {

	private final MobGenerationService mobGenerationService;
	private final AiJobRepository aiJobRepository;
	private final AiJobEventRepository aiJobEventRepository;
	private final GenerationEventBroadcaster eventBroadcaster;

	public GenerationJobController(
			MobGenerationService mobGenerationService,
			AiJobRepository aiJobRepository,
			AiJobEventRepository aiJobEventRepository,
			GenerationEventBroadcaster eventBroadcaster) {
		this.mobGenerationService = mobGenerationService;
		this.aiJobRepository = aiJobRepository;
		this.aiJobEventRepository = aiJobEventRepository;
		this.eventBroadcaster = eventBroadcaster;
	}

	@PostMapping("/api/mobs/{mobId}/generate")
	public ResponseEntity<StartGenerationResponse> start(@PathVariable UUID mobId) {
		UUID jobId = mobGenerationService.startGeneration(mobId);
		return ResponseEntity.accepted().body(new StartGenerationResponse(jobId));
	}

	/**
	 * `Last-Event-ID` (AC #3): el navegador lo reenvía automáticamente en
	 * toda reconexión de `EventSource` que haya recibido al menos un
	 * evento con `id:` -- ningún código de reconexión manual hace falta
	 * en el cliente.
	 *
	 * <p><b>Orden deliberado -- suscribir ANTES de leer el backlog</b>: si
	 * un evento se persiste justo entre ambos pasos, este emitter podría
	 * recibirlo DOS veces (una vía la suscripción en vivo, otra vía el
	 * backlog) -- preferible a la alternativa (leer backlog primero),
	 * que podría hacer perder ese mismo evento. El cliente deduplica por
	 * `seq` (idempotente), documentado también en `GenerationStep.vue`.
	 */
	@GetMapping(value = "/api/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter events(@PathVariable UUID jobId, @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
		if (!aiJobRepository.existsById(jobId)) {
			throw new JobNotFoundException(jobId);
		}
		int fromSeq = lastEventId != null ? Integer.parseInt(lastEventId) : 0;

		SseEmitter emitter = eventBroadcaster.subscribe(jobId);
		List<AiJobEventEntity> backlog = aiJobEventRepository.findByJobIdAndSeqGreaterThanOrderBySeqAsc(jobId, fromSeq);
		eventBroadcaster.replay(emitter, backlog);

		// Un cliente que se conecta/reconecta DESPUÉS de que el job ya
		// terminó nunca vería el `completeAll()` que el pipeline disparó
		// en el pasado (esa suscripción ni existía todavía) -- sin este
		// chequeo, el emitter de un reconecto tardío quedaría abierto
		// para siempre. Ventana residual reconocida, no oculta: si el job
		// pasa a estado terminal EXACTAMENTE entre `subscribe` y esta
		// relectura, esta suscripción puntual podría no cerrarse sola
		// (el próximo `GC`/timeout del contenedor la limpia igual) --
		// aceptable para la escala de este proyecto (ver
		// `GenerationEventBroadcaster`).
		AiJobEntity currentJob = aiJobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
		if (isTerminal(currentJob.getStatus())) {
			eventBroadcaster.completeAll(jobId);
		}
		return emitter;
	}

	private static boolean isTerminal(String status) {
		return "completed".equals(status) || "failed".equals(status) || "cancelled".equals(status);
	}

	@PostMapping("/api/jobs/{jobId}/cancel")
	public ResponseEntity<Void> cancel(@PathVariable UUID jobId) {
		mobGenerationService.requestCancellation(jobId);
		return ResponseEntity.accepted().build();
	}

}
