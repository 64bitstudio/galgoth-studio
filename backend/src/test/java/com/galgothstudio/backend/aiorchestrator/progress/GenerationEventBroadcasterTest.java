package com.galgothstudio.backend.aiorchestrator.progress;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Ticket 056 -- hallazgo real reproducido consistentemente por la suite
 * E2E de Fase 3 contra providers mock (029, `GenerationJobController`):
 * una generación mock termina tan rápido que `completeAll(jobId)` puede
 * correr en OTRO hilo exactamente mientras `replay()`/`publish()` todavía
 * están iterando sobre el mismo emitter -- `SseEmitter.send()` lanza
 * `IllegalStateException` (no `IOException`) si el emitter ya fue
 * completado por ese otro hilo. Antes de este fix, esa excepción se
 * propagaba sin capturar y tumbaba la request completa del endpoint SSE
 * con un 500 (el `EventSource` del navegador quedaba reconectando
 * indefinidamente, nunca viendo ningún progreso).
 */
class GenerationEventBroadcasterTest {

	private static AiJobEventEntity anEvent(UUID jobId, int seq) {
		AiJobEventEntity event = new AiJobEventEntity(UUID.randomUUID());
		event.setJobId(jobId);
		event.setSeq(seq);
		event.setStage("analyzing_reference");
		event.setMessage("Analizando referencia…");
		event.setProgressPct(10);
		event.setPayloadJson(null);
		event.setCreatedAt(Instant.now());
		return event;
	}

	@Test
	void replayAUnEmitterQueYaSeCompletoPorOtroHiloNoPropagaLaExcepcion() {
		GenerationEventBroadcaster broadcaster = new GenerationEventBroadcaster(new ObjectMapper());
		UUID jobId = UUID.randomUUID();
		SseEmitter emitter = broadcaster.subscribe(jobId);
		emitter.complete(); // simula completeAll(jobId) disparado por el pipeline async en otro hilo

		assertThatCode(() -> broadcaster.replay(emitter, List.of(anEvent(jobId, 1), anEvent(jobId, 2))))
				.doesNotThrowAnyException();
	}

	@Test
	void publishAUnEmitterQueYaSeCompletoPorOtroHiloNoPropagaLaExcepcion() {
		GenerationEventBroadcaster broadcaster = new GenerationEventBroadcaster(new ObjectMapper());
		UUID jobId = UUID.randomUUID();
		SseEmitter emitter = broadcaster.subscribe(jobId);
		emitter.complete();

		assertThatCode(() -> broadcaster.publish(jobId, anEvent(jobId, 1))).doesNotThrowAnyException();
	}

}
