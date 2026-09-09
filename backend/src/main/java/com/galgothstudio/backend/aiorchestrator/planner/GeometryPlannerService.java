package com.galgothstudio.backend.aiorchestrator.planner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.aiorchestrator.provider.ReasoningRequest;
import com.galgothstudio.backend.aiorchestrator.provider.StructuredReasoningProvider;
import com.galgothstudio.backend.domain.geometry.GeometryEngine;
import com.galgothstudio.backend.domain.geometry.GeometryOperation;
import com.galgothstudio.backend.domain.geometry.GeometryValidationException;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.domain.uv.UvLayoutStrategy;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * `ModelIntent` → `GeometryOperation[]` (ticket 028, HU-13/HU-15, master
 * prompt §9.2): segunda de las dos llamadas a IA del pipeline de
 * generación. El proveedor NUNCA devuelve un `.bbmodel` final -- solo
 * operaciones de la whitelist cerrada del Geometry Engine (005), que es
 * quien de verdad aplica la geometría (y, vía `AutoUv`/006, su UV) sobre
 * un modelo vacío. Un `op` fuera de la whitelist falla la
 * deserialización Jackson ANTES de que exista nada que aplicar (mismo
 * mecanismo ya usado en 005 para operaciones manuales/server-side) --
 * este planner no necesita revalidar la whitelist por separado, la
 * deserialización YA la garantiza.
 */
@Service
public class GeometryPlannerService {

	static final String PROMPT_VERSION = "planner-v1";
	static final String SCHEMA_VERSION = "geometry-operations-v1";

	private static final String SYSTEM_PROMPT =
			"""
			Eres un planificador de geometría de mobs de Minecraft Java Edition. \
			Dado un ModelIntent (silueta, proporciones, asimetría, rasgos, materiales), \
			devolvés un ARRAY JSON de operaciones -- NADA más, sin texto antes ni después, \
			sin envolver en un objeto.

			Cada elemento del array es UNA de estas 9 operaciones exactas (campo "op" \
			obligatorio, exactamente uno de estos 9 valores -- cualquier otro valor hace \
			fallar el batch completo):

			{"op":"createBone","tempId":"<id temporal único>","name":"<nombre>","parentId":"<tempId o id real, o null si es raíz>","pivot":[x,y,z],"rotation":[x,y,z]}
			{"op":"createCuboid","tempId":"<id temporal único>","name":"<nombre>","boneId":"<tempId o id real>","from":[x,y,z],"to":[x,y,z],"origin":[x,y,z],"rotation":[x,y,z]}
			{"op":"resizeCuboid","target":"<tempId o id real de un cuboid>","scale":[x,y,z]}
			{"op":"moveCuboid","target":"<tempId o id real de un cuboid>","delta":[x,y,z]}
			{"op":"rotateCuboid","target":"<tempId o id real de un cuboid>","rotationDeg":[x,y,z]}
			{"op":"setBonePivot","target":"<tempId o id real de un bone>","pivot":[x,y,z]}
			{"op":"setBoneRotation","target":"<tempId o id real de un bone>","rotation":[x,y,z]}
			{"op":"parentBone","target":"<tempId o id real de un bone>","newParentId":"<tempId o id real, o null>"}
			{"op":"removeCuboid","target":"<tempId o id real de un cuboid>"}

			Reglas del sistema de coordenadas (unidades = minecraft_pixels, ver
			docs/adr/0001-coordinate-system-contract.md): "from" siempre estrictamente \
			menor que "to" en cada eje (dimensiones positivas). Un "tempId" se puede \
			referenciar en operaciones POSTERIORES del mismo array (nunca en una \
			operación anterior). El primer bone (raíz) tiene "parentId": null.

			Genera una jerarquía de bones y cuboides COMPLETA y coherente para un mob \
			humanoide estándar de Minecraft (cabeza, torso, brazos, piernas como mínimo), \
			ajustando proporciones/asimetría/rasgos según el ModelIntent recibido, pero \
			SIEMPRE manteniendo el rig neutral y animable (nunca omitas un miembro \
			principal, nunca proporciones tan extremas que rompan el rig).
			""";

	private final StructuredReasoningProvider reasoningProvider;
	private final ObjectMapper objectMapper;
	private final UvLayoutStrategy uvLayoutStrategy;

	public GeometryPlannerService(StructuredReasoningProvider reasoningProvider, ObjectMapper objectMapper, UvLayoutStrategy uvLayoutStrategy) {
		this.reasoningProvider = reasoningProvider;
		this.objectMapper = objectMapper;
		this.uvLayoutStrategy = uvLayoutStrategy;
	}

	public GeometryPlanResult plan(ModelIntent modelIntent, MobProjectModel startingModel) {
		String userPrompt;
		try {
			userPrompt = "ModelIntent:\n" + objectMapper.writeValueAsString(modelIntent) + "\n\nDevolvé el array de operaciones.";
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo serializar un ModelIntent ya validado.", e);
		}

		ReasoningRequest request = new ReasoningRequest(SYSTEM_PROMPT, userPrompt, PROMPT_VERSION, SCHEMA_VERSION);
		AiProviderResponse response = reasoningProvider.reason(request);

		List<GeometryOperation> operations;
		try {
			operations = objectMapper.readValue(response.rawContent(), new TypeReference<List<GeometryOperation>>() {});
		} catch (Exception e) {
			throw new InvalidGeometryProposalException(
					"El StructuredReasoningProvider devolvió operaciones inválidas: " + e.getMessage(), response, e);
		}

		MobProjectModel result;
		try {
			result = GeometryEngine.apply(startingModel, operations, uvLayoutStrategy);
		} catch (GeometryValidationException e) {
			throw new InvalidGeometryProposalException(
					"La geometría propuesta no pasó la validación del Geometry Engine: " + e.getMessage(), response, e);
		}

		return new GeometryPlanResult(result, response);
	}

}
