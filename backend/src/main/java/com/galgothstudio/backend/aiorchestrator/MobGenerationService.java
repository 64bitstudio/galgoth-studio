package com.galgothstudio.backend.aiorchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventRepository;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.planner.GeometryPlannerService;
import com.galgothstudio.backend.aiorchestrator.planner.RawOperationsResult;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationCancellationRegistry;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationEventBroadcaster;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationPreviewDiff;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationStage;
import com.galgothstudio.backend.aiorchestrator.progress.PreviewDelta;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
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
import com.galgothstudio.backend.domain.export.BBModelExporterV5;
import com.galgothstudio.backend.domain.export.validation.FmmCompatibilityValidator;
import com.galgothstudio.backend.domain.export.validation.ValidationResult;
import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.uv.UvLayoutStrategy;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import com.galgothstudio.backend.project.persistence.MobEntity;
import com.galgothstudio.backend.project.persistence.MobRepository;
import com.galgothstudio.backend.project.persistence.ReferenceImageEntity;
import com.galgothstudio.backend.project.persistence.ReferenceImageRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
	private final UvLayoutStrategy uvLayoutStrategy;
	private final boolean geometryStreamingEnabled;
	private final long heartbeatInitialDelaySeconds;
	private final long heartbeatPeriodSeconds;

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
			@Qualifier("generationExecutor") Executor generationExecutor,
			UvLayoutStrategy uvLayoutStrategy,
			@Value("${ai.geometry-streaming-enabled}") boolean geometryStreamingEnabled,
			// Ticket 038 -- cadencia del ping de "sigue vivo" en modo heartbeat:
			// configurable (no una constante hardcodeada) para que los tests
			// puedan ejercitar el heartbeat real sin esperar los 8s de
			// producción. Defaults reales: ni tan seguido que sature
			// `ai_job_events`/el broadcaster, ni tan espaciado que el usuario
			// dude si el proceso murió durante los 70-90s reales de espera.
			@Value("${ai.geometry-heartbeat-initial-delay-seconds:8}") long heartbeatInitialDelaySeconds,
			@Value("${ai.geometry-heartbeat-period-seconds:8}") long heartbeatPeriodSeconds) {
		this.mobRepository = mobRepository;
		this.referenceImageRepository = referenceImageRepository;
		this.assetStorageService = assetStorageService;
		this.visionAnalysisService = visionAnalysisService;
		this.geometryPlannerService = geometryPlannerService;
		this.aiJobRepository = aiJobRepository;
		this.aiJobEventRepository = aiJobEventRepository;
		this.eventBroadcaster = eventBroadcaster;
		this.cancellationRegistry = cancellationRegistry;
		this.uvLayoutStrategy = uvLayoutStrategy;
		this.geometryStreamingEnabled = geometryStreamingEnabled;
		this.heartbeatInitialDelaySeconds = heartbeatInitialDelaySeconds;
		this.heartbeatPeriodSeconds = heartbeatPeriodSeconds;
		this.objectMapper = objectMapper;
		this.generationExecutor = generationExecutor;
	}

	/** Preflight síncrono (mob/referencia deben existir, AC implícito del ticket 028 preservado) + creación inmediata de la fila `running` -- el pipeline real se dispara después, en {@code generationExecutor}. */
	public UUID startGeneration(UUID mobId) {
		MobEntity mob = mobRepository.findById(mobId).orElseThrow(() -> new MobNotFoundException(mobId));
		ReferenceImageEntity reference = mostRecentReference(mobId);

		AiJobEntity job = newRunningJob(mob.getId(), reference.getId());
		aiJobRepository.save(job);

		GenerationJobContext context = new GenerationJobContext(
				job.getId(), mob.getId(), mob.getProjectId(), mob.getName(), mob.getBaseType(), reference.getId(), reference.getStorageKey(),
				reference.getContentType());

		generationExecutor.execute(() -> runPipeline(context));
		return context.jobId();
	}

	public void requestCancellation(UUID jobId) {
		AiJobEntity job = aiJobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
		if (!STATUS_RUNNING.equals(job.getStatus())) {
			throw new InvalidJobStateException(jobId, job.getStatus());
		}
		cancellationRegistry.requestCancel(jobId);
	}

	private void runPipeline(GenerationJobContext context) {
		UUID jobId = context.jobId();
		AtomicInteger seq = new AtomicInteger(0);
		try {
			checkCancellation(jobId);
			emit(jobId, seq, GenerationStage.ANALIZANDO_REFERENCIA, "Analizando imagen de referencia…", 5, null);

			byte[] imageBytes = assetStorageService
					.get(context.storageKey())
					.orElseThrow(() -> new IllegalStateException(
							"La imagen de referencia '" + context.referenceId() + "' no está en el storage."));

			ModelIntentAnalysisResult visionResult = visionAnalysisService.analyze(imageBytes, context.contentType(), context.baseType());
			updateJobProviderInfo(jobId, visionResult.providerResponse());
			checkCancellation(jobId);
			emit(jobId, seq, GenerationStage.DETECTANDO_SILUETA, "Silueta detectada: " + visionResult.modelIntent().silhouette(), 25, null);

			// Ticket 038 -- hallazgo real (ver ai_job_events de jobs reales en
			// dev, 2026-09-09): esta era la llamada que dejaba la UI "pegada"
			// 70-90s sin ningún evento. Con streaming=true, cada operación
			// real dispara su propio evento acá abajo (via applyStepAndEmit),
			// en vez de un replay post-hoc instantáneo.
			MobProjectModel emptyModel = emptyModelFor(context);
			GeometryPlanExecution planExecution = geometryStreamingEnabled
					? planWithStreaming(jobId, seq, visionResult.modelIntent(), emptyModel)
					: planWithHeartbeat(jobId, seq, visionResult.modelIntent(), emptyModel);
			updateJobProviderInfo(jobId, planExecution.providerResponse());
			checkCancellation(jobId);

			emit(jobId, seq, GenerationStage.PREPARANDO_RESULTADO, "Preparando resultado…", 90, null);
			MobProjectModel finalModel = geometryPlannerService.applyOperations(planExecution.operations(), planExecution.providerResponse(), emptyModel);
			checkCancellation(jobId);

			emit(jobId, seq, GenerationStage.VALIDANDO_GEOMETRIA, "Validando compatibilidad con Blockbench/FMM…", 95, null);
			validateFmmCompatibilityInformational(jobId, finalModel);

			completeJob(jobId, finalModel);
			emit(jobId, seq, GenerationStage.COMPLETADO, "Generación completada.", 100, previewSnapshotPayload(finalModel));
		} catch (GenerationCancelledException e) {
			cancelJob(jobId);
			emit(jobId, seq, GenerationStage.CANCELADO, e.getMessage(), null, null);
		} catch (GenerationValidationException e) {
			// InvalidModelIntentException (AC #1) e InvalidGeometryProposalException
			// (AC #2) -- mismo tratamiento para ambas, ver GenerationValidationException.
			failJob(jobId, e.providerResponse(), e.getMessage());
			emit(jobId, seq, GenerationStage.FALLIDO, e.getMessage(), null, null);
		} catch (RuntimeException e) {
			// Red caída/`AiProviderException`, storage inaccesible, o cualquier
			// otro fallo no anticipado -- nunca deja el job colgado en
			// `running` para siempre (AC implícito: cada job termina).
			log.error("Fallo inesperado en el pipeline de generación del job {}", jobId, e);
			failJob(jobId, null, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
			emit(jobId, seq, GenerationStage.FALLIDO, "Fallo inesperado durante la generación.", null, null);
		} finally {
			cancellationRegistry.clear(jobId);
			eventBroadcaster.completeAll(jobId);
		}
	}

	/** Resultado de la fase de planeamiento geométrico (streaming o heartbeat, ticket 038) -- las mismas 2 cosas que antes devolvía {@code requestOperations} (lista cruda + `AiProviderResponse`), ahora sin acoplar la aplicación final de UV a este paso. */
	private record GeometryPlanExecution(List<GeometryOperation> operations, AiProviderResponse providerResponse) {
	}

	/**
	 * Ticket 038 -- modo streaming (switch encendido, default): consume la
	 * respuesta del Geometry Planner incrementalmente y aplica/emite cada
	 * operación real EN CUANTO el modelo la termina de emitir -- a
	 * diferencia del modo heartbeat, acá no hay ningún replay post-hoc,
	 * el usuario ve el modelo crecer en tiempo real mientras la IA todavía
	 * está generando. Como bonus real (no buscado a propósito): la
	 * cancelación deja de estar limitada a "recién en el próximo punto de
	 * control" (ver `GenerationCancellationRegistry`) durante ESTA fase --
	 * cada operación parseada es un punto de control nuevo.
	 */
	private GeometryPlanExecution planWithStreaming(UUID jobId, AtomicInteger seq, ModelIntent modelIntent, MobProjectModel emptyModel) {
		List<GeometryOperation> collected = new ArrayList<>();
		MobProjectModel[] previewBox = {emptyModel};
		RawOperationsResult raw = geometryPlannerService.planStreaming(modelIntent, op -> {
			checkCancellation(jobId);
			collected.add(op);
			// Progreso honesto basado en operaciones REALES ya vistas -- no
			// se conoce el total hasta que el stream termina (a diferencia
			// del modo heartbeat, que sí conoce `operations.size()` de
			// entrada), así que se acerca asintóticamente a 89% en vez de
			// una fracción exacta de un total desconocido.
			int progressPct = Math.min(89, 40 + collected.size());
			previewBox[0] = applyStepAndEmit(jobId, seq, emptyModel, collected, previewBox[0], op, progressPct);
		});
		return new GeometryPlanExecution(raw.operations(), raw.providerResponse());
	}

	/**
	 * Ticket 038 -- modo heartbeat (switch operativo apagado,
	 * `AI_GEOMETRY_STREAMING_ENABLED=false`): la misma llamada bloqueante
	 * de siempre, pero con un ping periódico HONESTO mientras espera
	 * (mismo stage/% ya emitido, `detectando_silueta`/25% -- nunca inventa
	 * avance de etapa ni de porcentaje, solo informa cuánto tiempo real
	 * lleva corriendo). Al volver la respuesta completa, reproduce el
	 * batch de una vez (mismo comportamiento instantáneo de siempre en
	 * este modo -- es exactamente lo que hacía el pipeline antes de este
	 * ticket).
	 */
	private GeometryPlanExecution planWithHeartbeat(UUID jobId, AtomicInteger seq, ModelIntent modelIntent, MobProjectModel emptyModel) {
		ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
		long startNanos = System.nanoTime();
		ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
				() -> {
					long elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000L;
					emit(
							jobId, seq, GenerationStage.DETECTANDO_SILUETA,
							"Generando geometría… llevamos " + elapsedSeconds + "s, puede tardar hasta un minuto.", 25, null);
				},
				heartbeatInitialDelaySeconds, heartbeatPeriodSeconds, TimeUnit.SECONDS);

		RawOperationsResult raw;
		try {
			raw = geometryPlannerService.requestOperations(modelIntent);
		} finally {
			heartbeat.cancel(true);
			heartbeatScheduler.shutdownNow();
		}

		checkCancellation(jobId);
		replayOperationsWithPreview(jobId, seq, raw.operations(), emptyModel);
		return new GeometryPlanExecution(raw.operations(), raw.providerResponse());
	}

	/**
	 * Reproduce el batch de operaciones YA obtenido (nunca vuelve a
	 * llamar al proveedor) de a una operación por vez, emitiendo un
	 * evento `preview_operations` por cada cambio real -- usado solo en
	 * modo heartbeat (ticket 038); en modo streaming, {@link #applyStepAndEmit}
	 * se llama directo desde el callback de {@link #planWithStreaming} a
	 * medida que cada operación llega de verdad, sin este replay.
	 */
	private MobProjectModel replayOperationsWithPreview(UUID jobId, AtomicInteger seq, List<GeometryOperation> operations, MobProjectModel emptyModel) {
		MobProjectModel previous = emptyModel;
		for (int i = 0; i < operations.size(); i++) {
			checkCancellation(jobId);
			int progressPct = Math.min(89, 40 + (int) Math.round(45.0 * (i + 1) / operations.size()));
			previous = applyStepAndEmit(jobId, seq, emptyModel, operations.subList(0, i + 1), previous, operations.get(i), progressPct);
		}
		return previous;
	}

	/**
	 * Aplica el batch completo visto HASTA AHORA (siempre desde
	 * `emptyModel`) y diferencia contra el preview anterior -- mecanismo
	 * compartido entre el replay post-hoc (heartbeat) y el streaming real:
	 * `GeometryEngine.apply` asigna ids reales nuevos (`UUID.randomUUID()`)
	 * en cada llamada, así que no soporta "aplicar una operación más"
	 * incrementalmente sobre un modelo ya construido con ids estables --
	 * hay que re-aplicar desde cero cada vez y dejar que
	 * {@link GenerationPreviewDiff} calcule qué cambió de verdad. Costo
	 * ínfimo (operaciones en memoria, sin I/O) incluso para el tamaño de
	 * batch real de este proyecto.
	 */
	private MobProjectModel applyStepAndEmit(
			UUID jobId, AtomicInteger seq, MobProjectModel emptyModel, List<GeometryOperation> allOpsSoFar,
			MobProjectModel previousPreview, GeometryOperation justAdded, int progressPct) {
		MobProjectModel current = GeometryEngine.apply(emptyModel, allOpsSoFar);
		PreviewDelta delta = GenerationPreviewDiff.diff(previousPreview, current);
		if (!delta.isEmpty()) {
			String stage = isBoneOnlyOp(justAdded) ? GenerationStage.CREANDO_RIG : GenerationStage.GENERANDO_CUBOIDES;
			emit(jobId, seq, stage, describeOperation(justAdded), progressPct, previewOperationsPayload(delta));
		}
		return current;
	}

	/**
	 * FMM (013) informativo dentro del pipeline (ticket 038, decisión con
	 * VoBo del PO): un job con hallazgos FMM sigue llegando a `completado`
	 * igual que hoy -- {@code GenerationResultService#getResult} sigue
	 * siendo quien de verdad expone `fmmCompatible`/`fmmIssues` al
	 * frontend. Acá solo se hace VISIBLE la etapa real (antes corría en
	 * silencio recién en el `GET /result` posterior); un fallo AL CORRER
	 * la validación (no un resultado inválido, eso lo maneja
	 * `GenerationResultService` normalmente) no debe tumbar un job que
	 * por lo demás generó geometría válida.
	 */
	private void validateFmmCompatibilityInformational(UUID jobId, MobProjectModel finalModel) {
		try {
			String bbmodelJson = BBModelExporterV5.export(finalModel, uvLayoutStrategy);
			ValidationResult validation = FmmCompatibilityValidator.validate(bbmodelJson);
			if (!validation.pass()) {
				log.info("Job {} generó un modelo con hallazgos de compatibilidad FMM (informativo, no falla el job): {}", jobId, validation.issues());
			}
		} catch (RuntimeException e) {
			log.warn(
					"No se pudo correr la validación FMM informativa del job {} durante el pipeline -- se ignora, "
							+ "GenerationResultService la reintenta al servir GET /result.",
					jobId, e);
		}
	}

	private static boolean isBoneOnlyOp(GeometryOperation op) {
		return op instanceof CreateBone || op instanceof SetBonePivot || op instanceof SetBoneRotation || op instanceof ParentBone;
	}

	private static String describeOperation(GeometryOperation op) {
		return switch (op) {
			case CreateBone c -> "Creando hueso: " + c.name();
			case CreateCuboid c -> "Creando cuboid: " + c.name();
			case ResizeCuboid _ -> "Ajustando dimensiones…";
			case MoveCuboid _ -> "Ajustando posición…";
			case RotateCuboid _ -> "Ajustando rotación…";
			case SetBonePivot _ -> "Ajustando pivote del rig…";
			case SetBoneRotation _ -> "Ajustando rotación del rig…";
			case ParentBone _ -> "Ajustando jerarquía del rig…";
			case RemoveCuboid _ -> "Quitando cuboid…";
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
	private MobProjectModel emptyModelFor(GenerationJobContext context) {
		BaseType baseType = objectMapper.convertValue(context.baseType(), BaseType.class);
		return new MobProjectModel(
				context.mobId().toString(),
				context.projectId().toString(),
				context.mobName(),
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
