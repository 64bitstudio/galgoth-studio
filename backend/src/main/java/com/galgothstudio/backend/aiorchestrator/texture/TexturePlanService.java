package com.galgothstudio.backend.aiorchestrator.texture;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.aiorchestrator.provider.VisionAnalysisRequest;
import com.galgothstudio.backend.aiorchestrator.provider.VisionModelProvider;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TexturePlan;
import com.galgothstudio.backend.modelvalidation.TexturePlanValidator;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Imagen de referencia (ya subida en Fase 2) → `TexturePlan` (ticket
 * 052, Diseño técnico §11 punto 1 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`): primer paso del
 * pipeline de generación de textura, ANTES de cualquier llamada a
 * `ImageGenerationProvider`/OpenAI (esa parte es 054). Construye el
 * prompt a partir de la imagen + el modelo actual (bones/cuboids con
 * ids reales, mismo criterio que {@code AiGeometryEditPlannerService},
 * 031), invoca el {@link VisionModelProvider} configurado, y valida la
 * respuesta cruda contra el JSON Schema de `TexturePlan` ANTES de
 * deserializarla -- una respuesta que no valida nunca llega a
 * convertirse en un objeto Java ni a alimentar ningún paso de generación
 * de imagen (AC #2 del ticket).
 *
 * <p><b>Hallazgo real, señalado explícitamente (sin parche silencioso):
 * el Diseño técnico §11 y el propio ticket 052 nombran
 * `StructuredReasoningProvider`/`MockReasoningProvider` para este paso
 * -- pero `StructuredReasoningProvider.reason(ReasoningRequest)` es
 * puramente de TEXTO: `ReasoningRequest` no tiene ningún campo de
 * imagen ("sin imagen (a diferencia de VisionAnalysisRequest)", su
 * propio Javadoc, ticket 025) y `ClaudeReasoningProvider` delega en
 * `ClaudeMessagesClient.callWithText`, que nunca arma un content-block
 * de tipo `image`. Analizar de verdad la imagen de referencia (AC #1:
 * "a partir de la imagen de referencia ya subida") requiere el mismo
 * mecanismo multimodal que ya usa `VisionAnalysisService`/`ModelIntent`
 * (028) -- `VisionModelProvider.analyzeReferenceImage`, que sí adjunta
 * los bytes de imagen vía `ClaudeMessagesClient.callWithImage`. Este
 * servicio usa `VisionModelProvider` (y, en tests, `MockVisionProvider`)
 * en su lugar -- la interfaz correcta para "Claude mirando una imagen y
 * devolviendo JSON estructurado", NO `StructuredReasoningProvider`
 * (reservada a razonamiento sobre texto ya extraído, sin imagen
 * adjunta). No se tocó ninguna clase de `aiorchestrator/provider/` para
 * llegar a esta conclusión -- ambas interfaces se usan tal cual ya
 * existen. Reportado explícitamente para VoBo del Product Owner.</b>
 */
@Service
public class TexturePlanService {

	static final String PROMPT_VERSION = "texture-plan-v1";
	static final String SCHEMA_VERSION = "texture-plan-v1";

	private static final String SYSTEM_PROMPT =
			"""
			Eres un diseñador de texturas de mobs de Minecraft Java Edition. \
			Analizas una imagen de concept art y el modelo 3D (bones/cuboids con \
			sus ids y nombres reales) ya generado a partir de ella, y devolvés un \
			TexturePlan JSON ESTRICTO, sin texto adicional antes ni después del JSON.

			El TexturePlan debe tener EXACTAMENTE esta forma (sin campos extra):
			{
			  "boneLabels": [
			    {"boneId": "<id REAL de un bone del modelo recibido>", "boneName": "<nombre real>", "semanticLabel": "<etiqueta corta, ej. \\"torso\\", \\"cabeza\\">"}
			  ],
			  "palette": {
			    "dominantColorHex": "<color hex #RRGGBB dominante de la imagen>",
			    "accentColorHex": "<color hex #RRGGBB de acento de la imagen>"
			  },
			  "materialNotes": [
			    {"boneId": "<id REAL de un bone del modelo recibido>", "boneName": "<nombre real>", "face": "<una de: north, south, east, west, up, down>", "note": "<descripción corta de material/textura para esa cara>"}
			  ]
			}

			"boneId"/"boneName" SIEMPRE deben ser el id y nombre REALES de un bone \
			que YA existe en el modelo recibido -- nunca inventes un id. Cubrí TODOS \
			los bones del modelo en "boneLabels". En "materialNotes" priorizá las \
			caras visibles/relevantes de cada bone (no hace falta una entrada por \
			cada una de las 6 caras de cada bone si no aporta información nueva).
			""";

	private final VisionModelProvider visionModelProvider;
	private final TexturePlanValidator validator;
	private final ObjectMapper objectMapper;

	public TexturePlanService(VisionModelProvider visionModelProvider, TexturePlanValidator validator, ObjectMapper objectMapper) {
		this.visionModelProvider = visionModelProvider;
		this.validator = validator;
		this.objectMapper = objectMapper;
	}

	public TexturePlanAnalysisResult analyze(byte[] imageBytes, String contentType, MobProjectModel currentModel) {
		String userPrompt = buildUserPrompt(currentModel);
		VisionAnalysisRequest request =
				new VisionAnalysisRequest(imageBytes, contentType, SYSTEM_PROMPT, userPrompt, PROMPT_VERSION, SCHEMA_VERSION);

		AiProviderResponse response = visionModelProvider.analyzeReferenceImage(request);

		List<String> errors = validator.validate(response.rawContent());
		if (!errors.isEmpty()) {
			throw new InvalidTexturePlanException(response, errors);
		}

		TexturePlan texturePlan;
		try {
			texturePlan = objectMapper.readValue(response.rawContent(), TexturePlan.class);
		} catch (Exception e) {
			// El schema ya validó -- esto solo protegería contra un desajuste
			// schema/DTO real (un bug de programación, no una respuesta mala
			// del proveedor), pero se reporta con el mismo tipo de excepción
			// para que el caller lo maneje de forma uniforme (mismo criterio
			// que VisionAnalysisService, 028).
			throw new InvalidTexturePlanException(
					"No se pudo deserializar un TexturePlan ya validado: " + e.getMessage(), response, e);
		}

		return new TexturePlanAnalysisResult(texturePlan, response);
	}

	private String buildUserPrompt(MobProjectModel currentModel) {
		try {
			return "Modelo actual (bones/cuboids con ids y nombres reales):\n" + objectMapper.writeValueAsString(currentModel)
					+ "\n\nAnalizá la imagen adjunta y devolvé el TexturePlan JSON.";
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo serializar un MobProjectModel ya validado.", e);
		}
	}

}
