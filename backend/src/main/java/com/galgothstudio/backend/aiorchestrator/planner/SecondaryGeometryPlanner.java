package com.galgothstudio.backend.aiorchestrator.planner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.aiorchestrator.provider.ReasoningRequest;
import com.galgothstudio.backend.aiorchestrator.provider.StructuredReasoningProvider;
import com.galgothstudio.backend.domain.geometry.GeometryOperation;
import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * `ModelIntent` + anatomía primaria ya fijada → geometría secundaria
 * (ropa desgarrada, garras, cuernos, jirones -- lo que da identidad visual)
 * -- ticket 099, HU-2b. Reemplaza, para geometría, el rol que hasta este
 * ticket cumplía {@link GeometryPlannerService} (que seguía pidiéndole al
 * LLM la anatomía primaria completa "a ojo"): la anatomía primaria ya la
 * resuelve {@code PrimaryGeometryGenerator} (098) de forma 100%
 * determinista, y este planner concentra la ÚNICA libertad real que le
 * queda al LLM en el tramo de geometría -- acotada explícitamente a
 * {@code createCuboid} sobre bones YA existentes, validada por
 * {@code SecondaryGeometryConstraints} antes de aplicarse (ningún batch
 * inválido llega nunca al {@code GeometryEngine}).
 *
 * <p>{@code GeometryPlannerService.applyOperations} (el cómputo de atlas
 * UV) se sigue reutilizando tal cual para el batch combinado
 * primaria+secundaria -- es agnóstico de qué produjo las operaciones.
 */
@Service
public class SecondaryGeometryPlanner {

	static final String PROMPT_VERSION = "secondary-planner-v1";
	static final String SCHEMA_VERSION = "geometry-operations-secondary-v1";

	/**
	 * Presupuesto por defecto de cuboides secundarios mientras no existe
	 * todavía un selector de detalle geométrico conectado de punta a punta
	 * (eso es alcance del ticket 100) -- equivalente al extremo inferior del
	 * presupuesto MEDIUM del documento de definición (18-45 total, ~14 ya
	 * los cubre la anatomía primaria del template humanoide).
	 */
	public static final int DEFAULT_SECONDARY_BUDGET = 20;

	private static final String SYSTEM_PROMPT =
			"""
			Sos un especialista en geometría SECUNDARIA de mobs de Minecraft Java \
			Edition. La anatomía primaria (esqueleto + volumen esencial: cabeza, \
			torso, brazos, piernas) YA EXISTE y está fijada -- tu única tarea es \
			proponer cuboides ADICIONALES que le den identidad visual al personaje \
			según los rasgos/materiales detectados (ropa desgarrada, garras, \
			cuernos, jirones, protrusiones, placas, etc.).

			Devolvés un ARRAY JSON -- NADA más, sin texto antes ni después. Cada \
			elemento es EXACTAMENTE esta forma (el único tipo de operación permitido):

			{"op":"createCuboid","tempId":"<id único>","name":"<nombre>","boneId":"<uno de los bones primarios listados abajo>","from":[x,y,z],"to":[x,y,z],"origin":[x,y,z],"rotation":[x,y,z],"semanticPart":"<categoría corta en MAYÚSCULAS, ej. CLAW, HORN, TORN_CLOTH, JAW, LOINCLOTH, EMISSIVE_CRACK>"}

			Reglas estrictas:
			- NINGÚN otro tipo de operación (nunca createBone, nunca resizeCuboid/moveCuboid/rotateCuboid/removeCuboid, nunca toques la anatomía primaria).
			- "boneId" DEBE ser exactamente uno de los ids de bone primarios listados abajo -- nunca inventes un id nuevo.
			- "semanticPart" es OBLIGATORIO, nunca vacío.
			- Coordenadas en minecraft_pixels, mismo sistema que la anatomía primaria (docs/adr/0001-coordinate-system-contract.md): "from" siempre estrictamente menor que "to" en cada eje.
			- No agregues más elementos de los que el presupuesto indicado permite.
			- Priorizá los rasgos más característicos de la referencia -- silueta y proporciones ya las resuelve la anatomía primaria, tu trabajo es la IDENTIDAD del personaje.
			""";

	private final StructuredReasoningProvider reasoningProvider;
	private final ObjectMapper objectMapper;

	public SecondaryGeometryPlanner(StructuredReasoningProvider reasoningProvider, ObjectMapper objectMapper) {
		this.reasoningProvider = reasoningProvider;
		this.objectMapper = objectMapper;
	}

	/** {@code pivot} se incluye en el prompt para que el LLM ancle la geometría secundaria cerca de la articulación real -- ayuda a que pase el constraint de distancia razonable al pivote ({@code SecondaryGeometryConstraints}) de entrada, no solo por casualidad. */
	public record BoneDescriptor(String id, String name, Vec3 pivot) {
	}

	public RawOperationsResult requestOperations(ModelIntent modelIntent, List<BoneDescriptor> primaryBones, int budgetRemaining) {
		ReasoningRequest request = buildRequest(modelIntent, primaryBones, budgetRemaining);
		AiProviderResponse response = reasoningProvider.reason(request);

		List<GeometryOperation> operations;
		try {
			operations = objectMapper.readValue(response.rawContent(), new TypeReference<List<GeometryOperation>>() {});
		} catch (Exception e) {
			throw new InvalidGeometryProposalException(
					"El StructuredReasoningProvider devolvió geometría secundaria inválida: " + e.getMessage(), response, e);
		}
		return new RawOperationsResult(operations, response);
	}

	/** Variante streaming -- mismo mecanismo que {@link GeometryPlannerService#planStreaming}, reutilizando {@link StreamingOperationsParser} (genérico, no acoplado a un planner en particular). */
	public RawOperationsResult planStreaming(
			ModelIntent modelIntent, List<BoneDescriptor> primaryBones, int budgetRemaining, Consumer<GeometryOperation> onOperation) {
		ReasoningRequest request = buildRequest(modelIntent, primaryBones, budgetRemaining);
		List<GeometryOperation> collected = new ArrayList<>();
		StreamingOperationsParser parser = new StreamingOperationsParser(objectMapper, op -> {
			collected.add(op);
			onOperation.accept(op);
		});

		AiProviderResponse response;
		try {
			response = reasoningProvider.reasonStreaming(request, parser::feed);
		} catch (StreamingOperationParseException e) {
			throw new InvalidGeometryProposalException(
					"El StructuredReasoningProvider (streaming) devolvió una operación de geometría secundaria inválida: " + e.getMessage(), null, e);
		}

		// A diferencia de GeometryPlannerService (anatomía primaria completa
		// obligatoria), acá una respuesta vacía es válida -- un personaje
		// simple puede legítimamente no necesitar ninguna geometría
		// secundaria (ver HU-4: presupuestos, no cuotas rígidas).
		return new RawOperationsResult(List.copyOf(collected), response);
	}

	private ReasoningRequest buildRequest(ModelIntent modelIntent, List<BoneDescriptor> primaryBones, int budgetRemaining) {
		String bonesDescription = primaryBones.stream()
				.map(b -> "- " + b.id() + " (" + b.name() + "), pivot=[" + b.pivot().x() + "," + b.pivot().y() + "," + b.pivot().z() + "]")
				.collect(Collectors.joining("\n"));
		String userPrompt;
		try {
			userPrompt = "Bones primarios disponibles:\n" + bonesDescription
					+ "\n\nPresupuesto restante de cuboides secundarios: " + budgetRemaining
					+ "\n\nModelIntent:\n" + objectMapper.writeValueAsString(modelIntent)
					+ "\n\nDevolvé el array de operaciones de geometría secundaria (puede ser vacío si no hace falta ninguna).";
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo serializar un ModelIntent ya validado.", e);
		}
		return new ReasoningRequest(SYSTEM_PROMPT, userPrompt, PROMPT_VERSION, SCHEMA_VERSION);
	}
}
