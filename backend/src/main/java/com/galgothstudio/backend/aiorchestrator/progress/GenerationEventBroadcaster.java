package com.galgothstudio.backend.aiorchestrator.progress;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventEntity;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Registro EN MEMORIA de `SseEmitter`s activos por job (ticket 029, AC
 * #1/#3) -- mismo supuesto de instancia única del backend que
 * {@link GenerationCancellationRegistry}. `ai_job_events` (Postgres) es
 * la fuente de verdad persistida; este broadcaster solo empuja eventos
 * en vivo a quien esté conectado en ESTE momento, y sirve el backlog ya
 * persistido a quien se conecta/reconecta.
 *
 * <p>Sin timeout explícito en el `SseEmitter` (`new SseEmitter(0L)` --
 * 0 = sin límite en Spring): una generación real puede tardar más que
 * el timeout default de 30s de un `SseEmitter` mientras espera la
 * respuesta de la API de Anthropic.
 */
@Component
public class GenerationEventBroadcaster {

	private static final Logger log = LoggerFactory.getLogger(GenerationEventBroadcaster.class);

	private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByJob = new ConcurrentHashMap<>();
	private final ObjectMapper objectMapper;

	public GenerationEventBroadcaster(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public SseEmitter subscribe(UUID jobId) {
		SseEmitter emitter = new SseEmitter(0L);
		CopyOnWriteArrayList<SseEmitter> emitters = emittersByJob.computeIfAbsent(jobId, k -> new CopyOnWriteArrayList<>());
		emitters.add(emitter);
		emitter.onCompletion(() -> removeEmitter(jobId, emitter));
		emitter.onTimeout(() -> removeEmitter(jobId, emitter));
		emitter.onError(e -> removeEmitter(jobId, emitter));
		return emitter;
	}

	/** Reproduce el backlog persistido a UN solo emitter recién suscrito (reconexión vía `Last-Event-ID`, AC #3) -- nunca al resto de los suscriptores. */
	public void replay(SseEmitter emitter, List<AiJobEventEntity> backlog) {
		for (AiJobEventEntity event : backlog) {
			sendTo(emitter, event);
		}
	}

	/** Empuja un evento nuevo a TODOS los emitters activos del job (AC #1). */
	public void publish(UUID jobId, AiJobEventEntity event) {
		CopyOnWriteArrayList<SseEmitter> emitters = emittersByJob.get(jobId);
		if (emitters == null) {
			return;
		}
		for (SseEmitter emitter : emitters) {
			sendTo(emitter, event);
		}
	}

	private void sendTo(SseEmitter emitter, AiJobEventEntity event) {
		try {
			GenerationEventView view = GenerationEventView.from(event, objectMapper);
			emitter.send(SseEmitter.event().id(String.valueOf(event.getSeq())).name("progress").data(view, MediaType.APPLICATION_JSON));
		} catch (IOException _) {
			log.info("Cliente SSE desconectado del job {} -- se remueve el emitter.", event.getJobId());
			removeEmitter(event.getJobId(), emitter);
		}
	}

	/** Cierra y desregistra todos los emitters de un job -- llamado cuando el job alcanza estado terminal. */
	public void completeAll(UUID jobId) {
		CopyOnWriteArrayList<SseEmitter> emitters = emittersByJob.remove(jobId);
		if (emitters == null) {
			return;
		}
		for (SseEmitter emitter : emitters) {
			emitter.complete();
		}
	}

	private void removeEmitter(UUID jobId, SseEmitter emitter) {
		CopyOnWriteArrayList<SseEmitter> emitters = emittersByJob.get(jobId);
		if (emitters != null) {
			emitters.remove(emitter);
		}
	}

}
