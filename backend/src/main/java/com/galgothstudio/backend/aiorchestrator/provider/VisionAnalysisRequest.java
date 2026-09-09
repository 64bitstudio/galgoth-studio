package com.galgothstudio.backend.aiorchestrator.provider;

import java.util.Arrays;

/**
 * Pedido a un {@link VisionModelProvider}: la imagen de referencia
 * (bytes + content-type, mismo par que produce ticket 024) más los
 * prompts de sistema/usuario y las versiones de prompt/schema que el
 * CALLER quiere que se reflejen en la {@link AiProviderResponse} (ver
 * esa clase para el porqué).
 *
 * `equals`/`hashCode`/`toString` sobreescritos a propósito (Sonar
 * `S6218`, mismo hallazgo real del ticket 024): un record con un campo
 * `byte[]` compara por identidad de array por defecto, no por
 * contenido.
 */
public record VisionAnalysisRequest(
		byte[] imageBytes, String contentType, String systemPrompt, String userPrompt, String promptVersion, String schemaVersion) {

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof VisionAnalysisRequest(
				byte[] otherImageBytes,
				String otherContentType,
				String otherSystemPrompt,
				String otherUserPrompt,
				String otherPromptVersion,
				String otherSchemaVersion))) {
			return false;
		}
		return Arrays.equals(imageBytes, otherImageBytes)
				&& contentType.equals(otherContentType)
				&& systemPrompt.equals(otherSystemPrompt)
				&& userPrompt.equals(otherUserPrompt)
				&& promptVersion.equals(otherPromptVersion)
				&& schemaVersion.equals(otherSchemaVersion);
	}

	@Override
	public int hashCode() {
		int result = Arrays.hashCode(imageBytes);
		result = 31 * result + contentType.hashCode();
		result = 31 * result + systemPrompt.hashCode();
		result = 31 * result + userPrompt.hashCode();
		result = 31 * result + promptVersion.hashCode();
		result = 31 * result + schemaVersion.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return "VisionAnalysisRequest[imageBytes=" + imageBytes.length + " bytes, contentType=" + contentType
				+ ", promptVersion=" + promptVersion + ", schemaVersion=" + schemaVersion + "]";
	}

}
