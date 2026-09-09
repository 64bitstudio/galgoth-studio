package com.galgothstudio.backend.aiorchestrator.edit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.planner.InvalidGeometryProposalException;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.aiorchestrator.provider.ReasoningRequest;
import com.galgothstudio.backend.aiorchestrator.provider.StructuredReasoningProvider;
import com.galgothstudio.backend.domain.geometry.GeometryEngine;
import com.galgothstudio.backend.domain.geometry.GeometryValidationException;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.uv.UvLayoutStrategy;
import org.springframework.stereotype.Service;

/**
 * Edición conversacional por IA sobre un modelo YA EXISTENTE (ticket
 * 031, HU-17, master prompt §9.3) -- a diferencia de
 * {@link com.galgothstudio.backend.aiorchestrator.planner.GeometryPlannerService}
 * (028, arranca de un modelo VACÍO desde un `ModelIntent`), acá el
 * proveedor recibe el modelo ACTUAL completo (bones/cuboids con sus ids
 * reales) + una instrucción en lenguaje natural, y devuelve
 * `{"summary":"...","operations":[...]}` -- el mismo ejemplo literal
 * que {@code MockReasoningProvider} ya devolvía por defecto desde el
 * ticket 025 (nunca antes ejercitado por ningún flujo real hasta este
 * ticket).
 *
 * <p>Reutiliza {@link InvalidGeometryProposalException} (028) para el
 * camino de fallo -- misma forma de error (JSON inválido, operación
 * fuera de whitelist, geometría resultante inválida), mismo tratamiento
 * en el caller.
 */
@Service
public class AiGeometryEditPlannerService {

	static final String PROMPT_VERSION = "edit-planner-v1";
	static final String SCHEMA_VERSION = "geometry-edit-v1";

	private static final String SYSTEM_PROMPT =
			"""
			Eres un editor de geometría de mobs de Minecraft Java Edition. \
			Recibís el modelo ACTUAL completo (bones/cuboids con sus ids y \
			nombres reales) y una instrucción en lenguaje natural del usuario. \
			Devolvés un plan de cambio -- JSON ESTRICTO, sin texto antes ni \
			después, con EXACTAMENTE esta forma:

			{"summary":"<resumen corto y legible del cambio, en español>","operations":[...]}

			"operations" es un ARRAY de operaciones de la MISMA whitelist \
			cerrada de 9 operaciones -- cualquier otro valor de "op" hace \
			fallar el batch completo:

			{"op":"createBone","tempId":"<id temporal único>","name":"<nombre>","parentId":"<id real o tempId, o null si es raíz>","pivot":[x,y,z],"rotation":[x,y,z]}
			{"op":"createCuboid","tempId":"<id temporal único>","name":"<nombre>","boneId":"<id real o tempId>","from":[x,y,z],"to":[x,y,z],"origin":[x,y,z],"rotation":[x,y,z]}
			{"op":"resizeCuboid","target":"<id real de un cuboid>","scale":[x,y,z]}
			{"op":"moveCuboid","target":"<id real de un cuboid>","delta":[x,y,z]}
			{"op":"rotateCuboid","target":"<id real de un cuboid>","rotationDeg":[x,y,z]}
			{"op":"setBonePivot","target":"<id real de un bone>","pivot":[x,y,z]}
			{"op":"setBoneRotation","target":"<id real de un bone>","rotation":[x,y,z]}
			{"op":"parentBone","target":"<id real de un bone>","newParentId":"<id real o tempId, o null>"}
			{"op":"removeCuboid","target":"<id real de un cuboid>"}

			"target"/"boneId"/"parentId" SIEMPRE referencian el id REAL de un \
			bone/cuboid que YA existe en el modelo actual recibido -- nunca \
			inventes un id. La única excepción es un "tempId" nuevo (para \
			createBone/createCuboid), referenciable en operaciones \
			POSTERIORES del mismo array, igual que en la generación inicial.

			Modificá SOLO lo que la instrucción pide -- nunca reconstruyas ni \
			reemplaces el rig completo, nunca toques bones/cuboids que la \
			instrucción no menciona.
			""";

	private final StructuredReasoningProvider reasoningProvider;
	private final ObjectMapper objectMapper;
	private final UvLayoutStrategy uvLayoutStrategy;

	public AiGeometryEditPlannerService(StructuredReasoningProvider reasoningProvider, ObjectMapper objectMapper, UvLayoutStrategy uvLayoutStrategy) {
		this.reasoningProvider = reasoningProvider;
		this.objectMapper = objectMapper;
		this.uvLayoutStrategy = uvLayoutStrategy;
	}

	public EditPlanResult plan(MobProjectModel currentModel, String instruction) {
		String userPrompt;
		try {
			userPrompt = "Modelo actual:\n" + objectMapper.writeValueAsString(currentModel) + "\n\nInstrucción del usuario: " + instruction
					+ "\n\nDevolvé el plan de cambio.";
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo serializar un MobProjectModel ya validado.", e);
		}

		ReasoningRequest request = new ReasoningRequest(SYSTEM_PROMPT, userPrompt, PROMPT_VERSION, SCHEMA_VERSION);
		AiProviderResponse response = reasoningProvider.reason(request);

		RawEditPlan rawPlan;
		try {
			rawPlan = objectMapper.readValue(response.rawContent(), RawEditPlan.class);
		} catch (Exception e) {
			throw new InvalidGeometryProposalException(
					"El StructuredReasoningProvider devolvió un plan de edición inválido: " + e.getMessage(), response, e);
		}

		MobProjectModel afterModel;
		try {
			afterModel = GeometryEngine.apply(currentModel, rawPlan.operations(), uvLayoutStrategy);
		} catch (GeometryValidationException e) {
			throw new InvalidGeometryProposalException(
					"El plan de edición no pasó la validación del Geometry Engine: " + e.getMessage(), response, e);
		}

		return new EditPlanResult(rawPlan.summary(), afterModel, response);
	}

}
