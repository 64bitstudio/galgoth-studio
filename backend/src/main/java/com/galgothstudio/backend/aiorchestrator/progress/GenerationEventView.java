package com.galgothstudio.backend.aiorchestrator.progress;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventEntity;

/**
 * Forma sobre el wire de un evento SSE (ticket 029, AC #1) -- `payload`
 * se deserializa desde `AiJobEventEntity.payloadJson` a un `JsonNode`
 * real ANTES de responder, para que el cliente reciba un objeto JSON
 * anidado (`{"type":"preview_operations",...}` / `{"type":"preview_snapshot",...}`)
 * en vez de una cadena JSON escapada dentro de otra cadena.
 */
public record GenerationEventView(int seq, String stage, String message, Integer progressPct, JsonNode payload) {

	public static GenerationEventView from(AiJobEventEntity entity, ObjectMapper objectMapper) {
		JsonNode payload = null;
		String payloadJson = entity.getPayloadJson();
		if (payloadJson != null) {
			try {
				payload = objectMapper.readTree(payloadJson);
			} catch (Exception e) {
				throw new IllegalStateException("No se pudo parsear un payload_jsonb ya escrito por este mismo backend.", e);
			}
		}
		return new GenerationEventView(entity.getSeq(), entity.getStage(), entity.getMessage(), entity.getProgressPct(), payload);
	}

}
