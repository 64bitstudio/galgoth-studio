package com.galgothstudio.backend.aiorchestrator.provider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Doble determinista de {@link StructuredReasoningProvider} (master
 * prompt §20, ticket 025) -- SIN llamar a ningún servicio externo. Uso
 * EXCLUSIVO en tests/desarrollo (`AI_REASONING_PROVIDER=mock`, ver
 * `AiProviderConfig`). Ver `MockVisionProvider`/`AiProviderConfig` para
 * el porqué de la separación de clases.
 *
 * <p><b>Hallazgo real #1 (ticket 033, suite E2E de aceptación)</b>: esta
 * interfaz la comparten DOS llamadores reales con formas de respuesta
 * incompatibles entre sí -- {@code GeometryPlannerService} (planificador
 * de GENERACIÓN, `promptVersion="planner-v1"`, opera sobre un modelo
 * VACÍO, necesita `createBone`/`createCuboid`) y
 * {@code AiGeometryEditPlannerService} (edición, `promptVersion=
 * "edit-planner-v1"`, opera sobre un modelo YA EXISTENTE, necesita
 * `resizeCuboid`/`moveCuboid` sobre ids reales). El único default
 * literal que existía (el ejemplo de edición del master prompt §9.3)
 * nunca fallaba en los tests porque CADA test de generación llama
 * {@link #setNextResponse} explícitamente con su propio fixture -- pero
 * corriendo el backend real como servidor (sin ningún override posible
 * desde afuera, exactamente el caso de la suite Playwright) el paso de
 * generación fallaría de verdad. {@link #reason} ahora elige entre dos
 * defaults según `promptVersion`.
 *
 * <p><b>Hallazgo real #2, más sutil (mismo ticket)</b>: aun separando los
 * dos defaults, el de EDICIÓN seguía hardcodeado a los ids literales
 * `hand_right`/`hand_left`/`shoulder_right_detail` del master prompt
 * §9.3 -- funciona SOLO contra un fixture armado a mano con esos ids
 * exactos (como el de `AiGeometryEditPlannerServiceTest`, ticket 031),
 * nunca contra un modelo real generado por `createCuboid`: `GeometryEngine`
 * SIEMPRE asigna un UUID nuevo al crear (`tempId` es solo una referencia
 * de ese mismo batch, se descarta después -- ver `GeometryEngine.
 * applyCreateCuboid`), así que ningún cuboid "generado" termina
 * llamándose literalmente `hand_right`, sea con un provider mock o con
 * Claude real. Para un servidor real, el `target` correcto es el id REAL
 * (un UUID) que el modelo actual YA tiene -- exactamente lo que el
 * propio `SYSTEM_PROMPT` de `AiGeometryEditPlannerService` le pide al
 * proveedor real que lea del modelo recibido. Un mock estático no puede
 * "leer" nada, pero SÍ puede aplicar la misma idea con una regex simple
 * sobre `request.userPrompt()` (que ya incluye el modelo actual
 * serializado): si el modelo tiene un cuboid con id literal `hand_right`
 * (el fixture de 031), usa el ejemplo estático tal cual (cero cambio de
 * comportamiento para ese test); si no, extrae el id REAL del primer
 * cuboid del modelo y genera un plan equivalente apuntando a ÉL --
 * funciona contra cualquier modelo real, mock o Claude.
 */
public class MockReasoningProvider implements StructuredReasoningProvider {

	private static final String PROVIDER_NAME = "mock";
	private static final String DEFAULT_MODEL = "mock-model";

	/** `GeometryPlannerService.PROMPT_VERSION` (paquete `planner`, no importable directo desde acá sin acoplar los dos paquetes) -- valor literal replicado a propósito. */
	private static final String GENERATION_PROMPT_VERSION = "planner-v1";

	/** Primer cuboid serializado en el modelo actual dentro del `userPrompt` -- mismo orden de campos que el record `Cuboid` (id, name, boneId, ...), `boneId` distingue un cuboid de un bone (que tiene `parentId`, no `boneId`). */
	private static final Pattern FIRST_CUBOID_ID =
			Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"\\s*,\\s*\"name\"\\s*:\\s*\"[^\"]*\"\\s*,\\s*\"boneId\"");

	/**
	 * Rig humanoide mínimo (raíz + torso, cabeza, ambos brazos) desde un
	 * modelo VACÍO -- mismo estilo de whitelist (master prompt §9.2) que
	 * los fixtures ya usados en `GeometryPlannerServiceTest`, con más de
	 * un bone/cuboid para que la suite E2E de 033 ejercite una jerarquía
	 * real, no un cubo aislado.
	 */
	private static final String DEFAULT_GENERATION_RESPONSE =
			"""
			[
			  {"op":"createBone","tempId":"root","name":"body","parentId":null,"pivot":[0,0,0],"rotation":[0,0,0]},
			  {"op":"createCuboid","tempId":"c_torso","name":"torso","boneId":"root","from":[-4,0,-4],"to":[4,12,4],"origin":[0,6,0],"rotation":[0,0,0]},
			  {"op":"createBone","tempId":"head_bone","name":"head","parentId":"root","pivot":[0,12,0],"rotation":[0,0,0]},
			  {"op":"createCuboid","tempId":"c_head","name":"head","boneId":"head_bone","from":[-4,12,-4],"to":[4,20,4],"origin":[0,16,0],"rotation":[0,0,0]},
			  {"op":"createBone","tempId":"left_arm_bone","name":"left_arm","parentId":"root","pivot":[-4,10,0],"rotation":[0,0,0]},
			  {"op":"createCuboid","tempId":"c_left_arm","name":"left_arm","boneId":"left_arm_bone","from":[-8,2,-2],"to":[-4,10,2],"origin":[-6,6,0],"rotation":[0,0,0]},
			  {"op":"createBone","tempId":"right_arm_bone","name":"right_arm","parentId":"root","pivot":[4,10,0],"rotation":[0,0,0]},
			  {"op":"createCuboid","tempId":"c_right_arm","name":"right_arm","boneId":"right_arm_bone","from":[4,2,-2],"to":[8,10,2],"origin":[6,6,0],"rotation":[0,0,0]}
			]
			""";

	/** Ejemplo literal de propuesta de edición del master prompt §9.3 -- se usa tal cual cuando el modelo actual realmente tiene un cuboid `hand_right` (fixture de `AiGeometryEditPlannerServiceTest`, 031); ver {@link #defaultEditResponseFor}. */
	private static final String DEFAULT_EDIT_RESPONSE =
			"""
			{
			  "summary": "Increase both hands and add asymmetry to shoulders",
			  "operations": [
			    {"op": "resizeCuboid", "target": "hand_right", "scale": [1.2, 1.15, 1.2]},
			    {"op": "resizeCuboid", "target": "hand_left", "scale": [1.15, 1.1, 1.15]},
			    {"op": "moveCuboid", "target": "shoulder_right_detail", "delta": [-0.5, 0.25, 0]}
			  ]
			}
			""";

	private String explicitResponse;

	public void setNextResponse(String rawJson) {
		this.explicitResponse = rawJson;
	}

	@Override
	public AiProviderResponse reason(ReasoningRequest request) {
		String response = explicitResponse != null ? explicitResponse : defaultResponseFor(request);
		return new AiProviderResponse(response, PROVIDER_NAME, DEFAULT_MODEL, request.promptVersion(), request.schemaVersion());
	}

	private String defaultResponseFor(ReasoningRequest request) {
		if (GENERATION_PROMPT_VERSION.equals(request.promptVersion())) {
			return DEFAULT_GENERATION_RESPONSE;
		}
		return defaultEditResponseFor(request.userPrompt());
	}

	/**
	 * Ver el hallazgo #2 del docstring de la clase.
	 *
	 * <p>Hallazgo real #3 (ticket 056, suite E2E de Fase 3): la escala
	 * 1.2/1.2/1.2 de esta rama (introducida en el ticket 031, antes de que
	 * existiera densidad de texel/AutoUv real por packing, ticket 042)
	 * hace crecer el footprint del primer cuboid (torso, en el rig de
	 * {@link #DEFAULT_GENERATION_RESPONSE}) lo suficiente como para
	 * desbordar el atlas que `AlphaAutoPackStrategy` empaqueta HOY para
	 * ese mismo rig -- `UV_ATLAS_OVERFLOW` real, reproducido
	 * consistentemente corriendo el flujo completo (generar -> editar por
	 * IA) contra un servidor real con providers mock. Escala reducida a
	 * 1.05 -- sigue siendo un resize real y visible, dentro del headroom
	 * que el packing de hoy ya reserva.
	 */
	private String defaultEditResponseFor(String userPrompt) {
		if (userPrompt.contains("\"id\":\"hand_right\"")) {
			return DEFAULT_EDIT_RESPONSE;
		}
		Matcher matcher = FIRST_CUBOID_ID.matcher(userPrompt);
		if (!matcher.find()) {
			return DEFAULT_EDIT_RESPONSE; // sin ningún cuboid real detectable (ej. un test directo del mock con un userPrompt genérico) -- el estático es un default razonable de todas formas.
		}
		String realCuboidId = matcher.group(1);
		return """
				{"summary":"Resize the first cuboid found in the current model","operations":[{"op":"resizeCuboid","target":"%s","scale":[1.05,1.05,1.05]}]}
				"""
				.formatted(realCuboidId);
	}

}
