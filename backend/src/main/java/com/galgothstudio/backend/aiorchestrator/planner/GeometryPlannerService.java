package com.galgothstudio.backend.aiorchestrator.planner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.aiorchestrator.provider.ReasoningRequest;
import com.galgothstudio.backend.aiorchestrator.provider.StructuredReasoningProvider;
import com.galgothstudio.backend.domain.geometry.GeometryEngine;
import com.galgothstudio.backend.domain.geometry.GeometryOperation;
import com.galgothstudio.backend.domain.geometry.GeometryValidationException;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.uv.AtlasResolutionCalculator;
import com.galgothstudio.backend.domain.uv.TexelDensity;
import com.galgothstudio.backend.domain.uv.UvLayoutStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
		RawOperationsResult raw = requestOperations(modelIntent);
		MobProjectModel result = applyOperations(raw.operations(), raw.providerResponse(), startingModel);
		return new GeometryPlanResult(result, raw.operations(), raw.providerResponse());
	}

	/**
	 * Llama al proveedor y deserializa/whitelistea su respuesta -- SIN
	 * aplicar todavía al `GeometryEngine` (ticket 029, AC #1: el
	 * pipeline asíncrono necesita esta lista cruda para reproducirla de
	 * forma incremental como preview, antes de la aplicación final con
	 * UV en {@link #applyOperations}).
	 */
	public RawOperationsResult requestOperations(ModelIntent modelIntent) {
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

		return new RawOperationsResult(operations, response);
	}

	/**
	 * Ticket 038 -- variante streaming de {@link #requestOperations}: en
	 * vez de esperar el array completo, consume la respuesta del
	 * proveedor incrementalmente ({@link StructuredReasoningProvider#reasonStreaming})
	 * y entrega cada {@link GeometryOperation} real a {@code onOperation}
	 * EN CUANTO el modelo termina de emitirla -- nunca es un replay
	 * post-hoc de una lista ya completa. Con un proveedor que no soporta
	 * streaming real (Mock), {@code reasonStreaming} cae a su default (una
	 * sola entrega con la respuesta completa) y este método sigue
	 * funcionando idéntico, solo sin el beneficio de incrementalidad.
	 */
	public RawOperationsResult planStreaming(ModelIntent modelIntent, Consumer<GeometryOperation> onOperation) {
		String userPrompt;
		try {
			userPrompt = "ModelIntent:\n" + objectMapper.writeValueAsString(modelIntent) + "\n\nDevolvé el array de operaciones.";
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo serializar un ModelIntent ya validado.", e);
		}

		ReasoningRequest request = new ReasoningRequest(SYSTEM_PROMPT, userPrompt, PROMPT_VERSION, SCHEMA_VERSION);
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
					"El StructuredReasoningProvider (streaming) devolvió una operación inválida: " + e.getMessage(), null, e);
		}

		if (collected.isEmpty()) {
			throw new InvalidGeometryProposalException(
					"El StructuredReasoningProvider (streaming) no devolvió ninguna operación válida.", response, null);
		}

		return new RawOperationsResult(List.copyOf(collected), response);
	}

	/**
	 * Aplicación final CON UV (ticket 006) de un batch ya obtenido de
	 * {@link #requestOperations} -- nunca vuelve a llamar al proveedor.
	 *
	 * <p>Ticket 042, Diseño técnico §7: {@code startingModel} SIEMPRE
	 * arranca sin cuboids en este flujo (generación de un mob nuevo,
	 * único caso de producción que invoca este método -- ver
	 * {@code MobGenerationService.emptyModelFor}), así que este es
	 * exactamente el momento de "atlas inicial": nunca se confía en el
	 * {@code TextureDocument} placeholder de {@code startingModel} (antes
	 * de este ticket, un 128×128 hardcodeado) -- se aplica la geometría en
	 * dos pasadas: (1) sin UV, solo para conocer los cuboids reales que la
	 * IA propuso; (2) con el atlas correcto (footprint empaquetado a
	 * densidad {@link TexelDensity#X1}, potencia de 2 inmediatamente
	 * contenedora, vía {@link AtlasResolutionCalculator}) ya asignado a
	 * {@code TextureDocument.width/height} ANTES de invocar
	 * {@code uvLayoutStrategy}, que es quien de verdad empaqueta las
	 * regiones dentro de ese atlas ya bien dimensionado.
	 */
	public MobProjectModel applyOperations(List<GeometryOperation> operations, AiProviderResponse providerResponse, MobProjectModel startingModel) {
		try {
			List<Cuboid> proposedCuboids = GeometryEngine.apply(startingModel, operations).cuboids();
			MobProjectModel sizedStartingModel = withInitialAtlas(startingModel, proposedCuboids);
			return GeometryEngine.apply(sizedStartingModel, operations, uvLayoutStrategy);
		} catch (GeometryValidationException e) {
			throw new InvalidGeometryProposalException(
					"La geometría propuesta no pasó la validación del Geometry Engine: " + e.getMessage(), providerResponse, e);
		}
	}

	/** Reemplaza {@code texture}/{@code uv} de {@code startingModel} por el atlas inicial calculado a partir de {@code proposedCuboids} -- el resto del modelo (bones/cuboids todavía sin operar, siempre vacíos en este flujo) queda igual. */
	private static MobProjectModel withInitialAtlas(MobProjectModel startingModel, List<Cuboid> proposedCuboids) {
		AtlasResolutionCalculator.AtlasSize atlas = AtlasResolutionCalculator.computeAtlas(proposedCuboids, TexelDensity.X1);
		TextureDocument sizedTexture = new TextureDocument(atlas.width(), atlas.height(), startingModel.texture().storageKey());
		return new MobProjectModel(
				startingModel.mobId(), startingModel.projectId(), startingModel.name(), startingModel.baseType(),
				startingModel.units(), startingModel.bones(), startingModel.cuboids(), sizedTexture,
				new UvLayout(atlas.width(), atlas.height(), startingModel.uv().regions(), startingModel.uv().reservations()),
				startingModel.animations(), startingModel.exportSettings(), startingModel.referenceImages());
	}

}
