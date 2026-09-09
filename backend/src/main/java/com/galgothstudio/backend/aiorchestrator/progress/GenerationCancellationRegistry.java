package com.galgothstudio.backend.aiorchestrator.progress;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Registro EN MEMORIA de solicitudes de cancelación (ticket 029, AC #4)
 * -- deliberadamente no una columna leída por polling en cada paso: el
 * pipeline de generación corre en un solo hilo async por job dentro de
 * ESTA misma instancia del backend (mismo supuesto de instancia única
 * ya asumido por `GenerationEventBroadcaster`, consistente con "sin
 * multi-tenancy/escalado este ciclo" del doc de definición). `ai_jobs.status`
 * SIGUE siendo la fuente de verdad persistida una vez que la
 * cancelación se confirma -- este registro solo señala la intención
 * mientras el job todavía corre.
 *
 * <p>Cancelación real, honesta: NO interrumpe una llamada HTTP a la API
 * de IA ya en vuelo (no hay forma confiable de abortar un
 * `RestClient.retrieve()` bloqueante a mitad de request sin arriesgar
 * dejar el cliente HTTP en un estado inconsistente) -- se revisa en los
 * puntos de control entre etapas (antes/después de cada llamada a
 * proveedor, entre cada operación de geometría aplicada). El job se
 * detiene en el PRÓXIMO punto de control, no instantáneamente.
 */
@Component
public class GenerationCancellationRegistry {

	private final Set<UUID> cancelledJobIds = ConcurrentHashMap.newKeySet();

	public void requestCancel(UUID jobId) {
		cancelledJobIds.add(jobId);
	}

	public boolean isCancelled(UUID jobId) {
		return cancelledJobIds.contains(jobId);
	}

	/** Llamado una vez que el job alcanza un estado terminal -- evita crecimiento sin límite del set mientras el proceso backend vive. */
	public void clear(UUID jobId) {
		cancelledJobIds.remove(jobId);
	}

}
