package com.galgothstudio.backend.aiorchestrator.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.aiorchestrator.provider.VisionAnalysisRequest;
import com.galgothstudio.backend.aiorchestrator.provider.VisionModelProvider;
import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.modelvalidation.ModelIntentValidator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Vision → `ModelIntent` (ticket 028, HU-13, master prompt §9.1): primer
 * de las dos llamadas a IA del pipeline de generación (la segunda es
 * {@link com.galgothstudio.backend.aiorchestrator.planner.GeometryPlannerService}).
 * Construye el prompt, invoca el {@link VisionModelProvider} configurado
 * (`ClaudeProvider` default o `MockProvider` en tests, ticket 025), y
 * valida la respuesta cruda contra el JSON Schema de `ModelIntent` ANTES
 * de deserializarla -- una respuesta que no valida nunca llega a
 * convertirse en un objeto Java ni a alimentar el planner (AC #1).
 */
@Service
public class VisionAnalysisService {

	/**
	 * Versión del prompt/schema que viaja en cada {@link AiProviderResponse}
	 * y termina en `ai_jobs.prompt_version`/`schema_version` -- se
	 * incrementa manualmente cada vez que el texto de {@link #SYSTEM_PROMPT}
	 * cambia de forma que afecte el contrato de salida esperado
	 * (reproducibilidad real, master prompt §20).
	 */
	static final String PROMPT_VERSION = "vision-v1";
	static final String SCHEMA_VERSION = "model-intent-v1";

	private static final String SYSTEM_PROMPT =
			"""
			Eres un diseñador de mobs de Minecraft Java Edition. Analizas una imagen de \
			concept art y describís su intención de diseño como un ModelIntent JSON \
			ESTRICTO, sin texto adicional antes ni después del JSON.

			El ModelIntent debe tener EXACTAMENTE esta forma (sin campos extra):
			{
			  "silhouette": "<descripción corta de la silueta general>",
			  "proportions": {
			    "headScale": <número > 0, 1.0 = proporción vanilla de Minecraft>,
			    "armLength": <número > 0>,
			    "handScale": <número > 0>,
			    "shoulderWidth": <número > 0>
			  },
			  "asymmetry": <número entre 0 y 1, 0 = simétrico, 1 = máxima asimetría deliberada>,
			  "features": ["<rasgo visual distintivo>", ...],
			  "materials": ["<descripción de material/color>", ...]
			}

			El resultado debe permanecer NEUTRAL Y ANIMABLE: nunca propongas huesos/miembros \
			faltantes, proporciones tan extremas que rompan el rigging humanoide estándar, ni \
			geometría desconectada del cuerpo principal.
			""";

	private final VisionModelProvider visionModelProvider;
	private final ModelIntentValidator validator;
	private final ObjectMapper objectMapper;

	public VisionAnalysisService(VisionModelProvider visionModelProvider, ModelIntentValidator validator, ObjectMapper objectMapper) {
		this.visionModelProvider = visionModelProvider;
		this.validator = validator;
		this.objectMapper = objectMapper;
	}

	public ModelIntentAnalysisResult analyze(byte[] imageBytes, String contentType, String baseType) {
		String userPrompt = "Tipo base del mob (restricción de rig): " + baseType
				+ ". Analiza la imagen adjunta y devolvé el ModelIntent JSON.";
		VisionAnalysisRequest request =
				new VisionAnalysisRequest(imageBytes, contentType, SYSTEM_PROMPT, userPrompt, PROMPT_VERSION, SCHEMA_VERSION);

		AiProviderResponse response = visionModelProvider.analyzeReferenceImage(request);

		List<String> errors = validator.validate(response.rawContent());
		if (!errors.isEmpty()) {
			throw new InvalidModelIntentException(response, errors);
		}

		ModelIntent intent;
		try {
			intent = objectMapper.readValue(response.rawContent(), ModelIntent.class);
		} catch (Exception e) {
			// El schema ya validó -- esto solo protegería contra un desajuste
			// schema/DTO real (un bug de programación, no una respuesta mala
			// del proveedor), pero se reporta con el mismo tipo de excepción
			// para que el caller lo maneje de forma uniforme.
			throw new InvalidModelIntentException(response, List.of("No se pudo deserializar un JSON ya validado: " + e.getMessage()));
		}

		return new ModelIntentAnalysisResult(intent, response);
	}

}
