package com.galgothstudio.backend.aiorchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventRepository;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.planner.GeometryPlannerService;
import com.galgothstudio.backend.aiorchestrator.planner.InvalidGeometryProposalException;
import com.galgothstudio.backend.aiorchestrator.planner.RawOperationsResult;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationCancellationRegistry;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationEventBroadcaster;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationPreviewDiff;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationStage;
import com.galgothstudio.backend.aiorchestrator.progress.PreviewDelta;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.aiorchestrator.vision.InvalidModelIntentException;
import com.galgothstudio.backend.aiorchestrator.vision.ModelIntentAnalysisResult;
import com.galgothstudio.backend.aiorchestrator.vision.VisionAnalysisService;
import com.galgothstudio.backend.asset.AssetStorageService;
import com.galgothstudio.backend.domain.geometry.CreateBone;
import com.galgothstudio.backend.domain.geometry.CreateCuboid;
import com.galgothstudio.backend.domain.geometry.GeometryEngine;
import com.galgothstudio.backend.domain.geometry.GeometryOperation;
import com.galgothstudio.backend.domain.geometry.MoveCuboid;
import com.galgothstudio.backend.domain.geometry.ParentBone;
import com.galgothstudio.backend.domain.geometry.RemoveCuboid;
import com.galgothstudio.backend.domain.geometry.ResizeCuboid;
import com.galgothstudio.backend.domain.geometry.RotateCuboid;
import com.galgothstudio.backend.domain.geometry.SetBonePivot;
import com.galgothstudio.backend.domain.geometry.SetBoneRotation;
import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import com.galgothstudio.backend.project.persistence.MobEntity;
import com.galgothstudio.backend.project.persistence.MobRepository;
import com.galgothstudio.backend.project.persistence.ReferenceImageEntity;
import com.galgothstudio.backend.project.persistence.ReferenceImageRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Orquesta el pipeline completo de generación (ticket 028/029, master
 * prompt §5 pasos 1-3): Vision → `ModelIntent` validado → Geometry
 * planner → `MobProjectModel` propuesto, con progreso/preview en vivo
 * vía SSE (HU-11). {@link #startGeneration} es SÍNCRONO y rápido (crea
 * la fila `ai_jobs` en estado `running` y devuelve su id de inmediato);
 * el pipeline real corre en {@code generationExecutor} (ticket 029,
 * {@link GenerationExecutorConfig}), fuera de cualquier transacción --
 * cada escritura a `ai_jobs`/`ai_job_events` es su propia transacción
 * corta (vía los métodos self-transaccionales de Spring Data), nunca UNA
 * transacción larga sosteniendo una conexión de BD mientras se espera
 * una llamada HTTP lenta a la API de un proveedor de IA.
 *
 * <p><b>El hallazgo de ticket 028 sobre fallos de red sin fila en
 * `ai_jobs` queda cerrado acá</b>: la fila se crea ANTES de la primera
 * llamada a cualquier proveedor (con `provider`/`model` en un valor
 * placeholder, actualizado a los reales en cuanto la primera respuesta
 * llega) -- un fallo de red ahora SÍ deja una fila `failed` real, con
 * `provider`/`model` en el placeholder si el fallo ocurrió antes de
 * recibir ninguna respuesta.
 */
@Service
public class MobGenerationService {

	private static final Logger log = LoggerFactory.getLogger(MobGenerationService.class);

	private static final String JOB_TYPE_GENERATE = "generate";
	private static final String STATUS_RUNNING = "running";
	private static final String STATUS_COMPLETED = "completed";
	private static final String STATUS_FAILED = "failed";
	private static final String STATUS_CANCELLED = "cancelled";
	/** `provider`/`model`/`prompt_version`/`schema_version` son `NOT NULL` (003) pero todavía no hay ninguna {@link AiProviderResponse} real cuando se crea la fila -- se sobreescribe en cuanto la primera respuesta llega, ver {@link #updateJobProviderInfo}. */
	private static final String PENDING_PLACEHOLDER = "pending";

	/** Ver la nota equivalente del ticket 028: 128x128 es la resolución recomendada por defecto del wizard (027) -- deja margen real para que un rig humanoide completo nunca dispare `UvAtlasOverflowException`. */
	private static final TextureDocument DEFAULT_TEXTURE = new TextureDocument(128, 128, null);

	private final MobRepository mobRepository;
	private final ReferenceImageRepository referenceImageRepository;
	private final AssetStorageService assetStorageService;
	private final VisionAnalysisService visionAnalysisService;
	private final GeometryPlannerService geometryPlannerService;
	private final AiJobRepository aiJobRepository;
	private final AiJobEventRepository aiJobEventRepository;
	private final GenerationEventBroadcaster eventBroadcaster;
	private final GenerationCancellationRegistry cancellationRegistry;
	private final ObjectMapper objectMapper;
	private final Executor generationExecutor;

	public MobGenerationService(
			MobRepository mobRepository,
			ReferenceImageRepository referenceImageRepository,
			AssetStorageService assetStorageService,
			VisionAnalysisService visionAnalysisService,
			GeometryPlannerService geometryPlannerService,
			AiJobRepository aiJobRepository,
			AiJobEventRepository aiJobEventRepository,
			GenerationEventBroadcaster eventBroadcaster,
			GenerationCancellationRegistry cancellationRegistry,
			ObjectMapper objectMapper,
			@Qualifier("generationExecutor") Executor generationExecutor) {
		this.mobRepository = mobRepository;
		this.referenceImageRepository = referenceImageRepository;
		this.assetStorageService = assetStorageService;
		this.visionAnalysisService = visionAnalysisService;
		this.geometryPlannerService = geometryPlannerService;
		this.aiJobRepository = aiJobRepository;
		this.aiJobEventRepository = aiJobEventRepository;
		this.eventBroadcaster = eventBroadcaster;
		this.cancellationRegistry = cancellationRegistry;
		this.objectMapper = objectMapper;
		this.generationExecutor = generationExecutor;
	}

	/** Preflight síncrono (mob/referencia deben existir, AC implícito del ticket 028 preservado) + creación inmediata de la fila `running` -- el pipeline real se dispara después, en {@code generationExecutor}. */
	public UUID startGeneration(UUID mobId) {
		MobEntity mob = mobRepository.findById(mobId).orElseThrow(() -> new MobNotFoundException(mobId));
		ReferenceImageEntity reference = mostRecentReference(mobId);

		AiJobEntity job = newRunningJob(mob.getId(), reference.getId());
		aiJobRepository.save(job);

		UUID jobId = job.getId();
		UUID projectId = mob.getProjectId();
		String mobName = mob.getName();
		String baseType = mob.getBaseType();
		UUID referenceId = reference.getId();
		String storageKey = reference.getStorageKey();
		String contentType = reference.getContentType();

		generationExecutor.execute(() -> runPipeline(jobId, mobId, projectId, mobName, baseType, referenceId, storageKey, contentType));
		return jobId;
	}

	public void requestCancellation(UUID jobId) {
		AiJobEntity job = aiJobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
		if (!STATUS_RUNNING.equals(job.getStatus())) {
			throw new InvalidJobStateException(jobId, job.getStatus());
		}
		cancellationRegistry.requestCancel(jobId);
	}

	private void runPipeline(
			UUID jobId, UUID mobId, UUID projectId, String mobName, String baseType, UUID referenceId, String storageKey, String contentType) {
		AtomicInteger seq = new AtomicInteger(0);
		try {
			checkCancellation(jobId);
			emit(jobId, seq, GenerationStage.ANALIZANDO_REFERENCIA, "Analizando imagen de referencia…", 5, null);

			byte[] imageBytes = assetStorageService
					.get(storageKey)
					.orElseThrow(() -> new IllegalStateException("La imagen de referencia '" + referenceId + "' no está en el storage."));

			ModelIntentAnalysisResult visionResult = visionAnalysisService.analyze(imageBytes, contentType, baseType);
			updateJobProviderInfo(jobId, visionResult.providerResponse());
			checkCancellation(jobId);
			emit(jobId, seq, GenerationStage.DETECTANDO_SILUETA, "Silueta detectada: " + visionResult.modelIntent().silhouette(), 25, null);

			RawOperationsResult raw = geometryPlannerService.requestOperations(visionResult.modelIntent());
			updateJobProviderInfo(jobId, raw.providerResponse());
			checkCancellation(jobId);

			MobProjectModel emptyModel = emptyModelFor(mobId, projectId, mobName, baseType);
			MobProjectModel finalModel = replayOperationsWithPreview(jobId, seq, raw, emptyModel);

			completeJob(jobId, finalModel);
			emit(jobId, seq, GenerationStage.COMPLETADO, "Generación completada.", 100, previewSnapshotPayload(finalModel));
		} catch (GenerationCancelledException e) {
			cancelJob(jobId);
			emit(jobId, seq, GenerationStage.CANCELADO, e.getMessage(), null, null);
		} catch (InvalidModelIntentException e) {
			failJob(jobId, e.providerResponse(), e.getMessage());
			emit(jobId, seq, GenerationStage.FALLIDO, e.getMessage(), null, null);
		} catch (InvalidGeometryProposalException e) {
			failJob(jobId, e.providerResponse(), e.getMessage());
			emit(jobId, seq, GenerationStage.FALLIDO, e.getMessage(), null, null);
		} catch (RuntimeException e) {
			// Red-caída/`AiProviderException`, storage inaccesible, o cualquier
			// otro fallo no anticipado -- nunca deja el job colgado en
			// `running` para siempre (AC implícito: todo job termina).
			log.error("Fallo inesperado en el pipeline de generación del job {}", jobId, e);
			failJob(jobId, null, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
			emit(jobId, seq, GenerationStage.FALLIDO, "Fallo inesperado durante la generación.", null, null);
		} finally {
			cancellationRegistry.clear(jobId);
			eventBroadcaster.completeAll(jobId);
		}
	}

	/**
	 * Reproduce el batch de operaciones YA obtenido (nunca vuelve a
	 * llamar al proveedor) de a una operación por vez, emitiendo un
	 * evento `preview_operations` por cada cambio real -- el mismo
	 * `GeometryEngine` (005) es la única autoridad, tanto para el
	 * preview incremental (sin UV, más barato) como para el resultado
	 * final (con UV, vía {@link GeometryPlannerService#applyOperations}).
	 */
	private MobProjectModel replayOperationsWithPreview(UUID jobId, AtomicInteger seq, RawOperationsResult raw, MobProjectModel emptyModel) {
		List<GeometryOperation> operations = raw.operations();
		MobProjectModel previous = emptyModel;
		for (int i = 0; i < operations.size(); i++) {
			checkCancellation(jobId);
			MobProjectModel current = GeometryEngine.apply(emptyModel, operations.subList(0, i + 1));
			PreviewDelta delta = GenerationPreviewDiff.diff(previous, current);
			if (!delta.isEmpty()) {
				GeometryOperation op = operations.get(i);
				String stage = isBoneOnlyOp(op) ? GenerationStage.CREANDO_RIG : GenerationStage.GENERANDO_CUBOIDES;
				int progressPct = Math.min(89, 40 + (int) Math.round(45.0 * (i + 1) / operations.size()));
				emit(jobId, seq, stage, describeOperation(op), progressPct, previewOperationsPayload(delta));
			}
			previous = current;
		}
		checkCancellation(jobId);
		return geometryPlannerService.applyOperations(operations, raw.providerResponse(), emptyModel);
	}

	private static boolean isBoneOnlyOp(GeometryOperation op) {
		return op instanceof CreateBone || op instanceof SetBonePivot || op instanceof SetBoneRotation || op instanceof ParentBone;
	}

	private static String describeOperation(GeometryOperation op) {
		return switch (op) {
			case CreateBone c -> "Creando hueso: " + c.name();
			case CreateCuboid c -> "Creando cuboid: " + c.name();
			case ResizeCuboid ignored -> "Ajustando dimensiones…";
			case MoveCuboid ignored -> "Ajustando posición…";
			case RotateCuboid ignored -> "Ajustando rotación…";
			case SetBonePivot ignored -> "Ajustando pivote del rig…";
			case SetBoneRotation ignored -> "Ajustando rotación del rig…";
			case ParentBone ignored -> "Ajustando jerarquía del rig…";
			case RemoveCuboid ignored -> "Quitando cuboid…";
		};
	}

	private void checkCancellation(UUID jobId) {
		if (cancellationRegistry.isCancelled(jobId)) {
			throw new GenerationCancelledException();
		}
	}

	private void emit(UUID jobId, AtomicInteger seq, String stage, String message, Integer progressPct, JsonNode payload) {
		AiJobEventEntity event = new AiJobEventEntity(UUID.randomUUID());
		event.setJobId(jobId);
		event.setSeq(seq.incrementAndGet());
		event.setStage(stage);
		event.setMessage(message);
		event.setProgressPct(progressPct);
		event.setPayloadJson(payload == null ? null : payload.toString());
		event.setCreatedAt(Instant.now());
		aiJobEventRepository.save(event);
		eventBroadcaster.publish(jobId, event);
	}

	private JsonNode previewOperationsPayload(PreviewDelta delta) {
		ObjectNode node = objectMapper.createObjectNode();
		node.put("type", "preview_operations");
		node.set("addedOrUpdatedBones", objectMapper.valueToTree(delta.addedOrUpdatedBones()));
		node.set("addedOrUpdatedCuboids", objectMapper.valueToTree(delta.addedOrUpdatedCuboids()));
		node.set("removedCuboidIds", objectMapper.valueToTree(delta.removedCuboidIds()));
		return node;
	}

	private JsonNode previewSnapshotPayload(MobProjectModel model) {
		// Único punto donde se emite `preview_snapshot` (AC: solo
		// resincronización/fallback) -- el evento final, cuando de todas
		// formas existe un `MobProjectModel` completo y autoritativo, así
		// que un cliente que perdió deltas individuales igual llega a un
		// estado final correcto con este único evento.
		ObjectNode node = objectMapper.createObjectNode();
		node.put("type", "preview_snapshot");
		node.set("model", objectMapper.valueToTree(model));
		return node;
	}

	private ReferenceImageEntity mostRecentReference(UUID mobId) {
		List<ReferenceImageEntity> references = referenceImageRepository.findByMobIdOrderByCreatedAtAsc(mobId);
		if (references.isEmpty()) {
			throw new NoReferenceImageException(mobId);
		}
		return references.getLast();
	}

	/** Modelo vacío (sin bones/cuboids) desde el que arranca el Geometry planner -- HU-10 AC #3: nada persistido todavía, el mob real conserva `current_revision_number=0` hasta "Usar este modelo" (030). */
	private MobProjectModel emptyModelFor(UUID mobId, UUID projectId, String mobName, String baseTypeRaw) {
		BaseType baseType = objectMapper.convertValue(baseTypeRaw, BaseType.class);
		return new MobProjectModel(
				mobId.toString(),
				projectId.toString(),
				mobName,
				baseType,
				MobProjectModel.UNITS_MINECRAFT_PIXELS,
				List.of(),
				List.of(),
				DEFAULT_TEXTURE,
				new UvLayout(DEFAULT_TEXTURE.width(), DEFAULT_TEXTURE.height(), List.of()),
				List.of(),
				new ExportSettings(FormatVersion.V5),
				List.of());
	}

	private AiJobEntity newRunningJob(UUID mobId, UUID referenceId) {
		Instant now = Instant.now();
		AiJobEntity job = new AiJobEntity(UUID.randomUUID());
		job.setMobId(mobId);
		job.setJobType(JOB_TYPE_GENERATE);
		job.setStatus(STATUS_RUNNING);
		job.setProvider(PENDING_PLACEHOLDER);
		job.setModel(PENDING_PLACEHOLDER);
		job.setPromptVersion(PENDING_PLACEHOLDER);
		job.setSchemaVersion(PENDING_PLACEHOLDER);
		job.setReferenceIds(writeJson(List.of(referenceId.toString())));
		// job_type='generate' -- sin revisión/draft previos, ver el CHECK de ai_jobs (ticket 003).
		job.setBaseRevisionNumber(null);
		job.setBaseDraftVersion(null);
		job.setCreatedAt(now);
		job.setStartedAt(now);
		return job;
	}

	private void updateJobProviderInfo(UUID jobId, AiProviderResponse response) {
		AiJobEntity job = aiJobRepository.findById(jobId).orElseThrow();
		job.setProvider(response.provider());
		job.setModel(response.model());
		job.setPromptVersion(response.promptVersion());
		job.setSchemaVersion(response.schemaVersion());
		aiJobRepository.save(job);
	}

	private void completeJob(UUID jobId, MobProjectModel model) {
		AiJobEntity job = aiJobRepository.findById(jobId).orElseThrow();
		job.setStatus(STATUS_COMPLETED);
		job.setProposalJson(writeJson(model));
		job.setFinishedAt(Instant.now());
		aiJobRepository.save(job);
	}

	/** `response` es `null` cuando el fallo ocurrió ANTES de recibir ninguna respuesta de proveedor (ej. `AiProviderException` de red) -- el placeholder de {@link #newRunningJob} queda tal cual, honesto sobre lo que realmente se sabe. */
	private void failJob(UUID jobId, AiProviderResponse response, String errorMessage) {
		AiJobEntity job = aiJobRepository.findById(jobId).orElseThrow();
		if (response != null) {
			job.setProvider(response.provider());
			job.setModel(response.model());
			job.setPromptVersion(response.promptVersion());
			job.setSchemaVersion(response.schemaVersion());
		}
		job.setStatus(STATUS_FAILED);
		job.setError(errorMessage);
		job.setFinishedAt(Instant.now());
		aiJobRepository.save(job);
	}

	private void cancelJob(UUID jobId) {
		AiJobEntity job = aiJobRepository.findById(jobId).orElseThrow();
		job.setStatus(STATUS_CANCELLED);
		job.setFinishedAt(Instant.now());
		aiJobRepository.save(job);
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo serializar un valor de dominio ya validado a JSON.", e);
		}
	}

}
