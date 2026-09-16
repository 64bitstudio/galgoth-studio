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
import com.galgothstudio.backend.aiorchestrator.planner.SecondaryGeometryPlanner;
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
import com.galgothstudio.backend.domain.geometry.PrimaryGeometryGenerator;
import com.galgothstudio.backend.domain.geometry.PrimaryOperationsResult;
import com.galgothstudio.backend.domain.geometry.RemoveCuboid;
import com.galgothstudio.backend.domain.geometry.ResizeCuboid;
import com.galgothstudio.backend.domain.geometry.RotateCuboid;
import com.galgothstudio.backend.domain.geometry.SecondaryGeometryConstraints;
import com.galgothstudio.backend.domain.geometry.SetBonePivot;
import com.galgothstudio.backend.domain.geometry.SetBoneRotation;
import com.galgothstudio.backend.domain.export.BBModelExporterV5;
import com.galgothstudio.backend.domain.export.validation.FmmCompatibilityValidator;
import com.galgothstudio.backend.domain.export.validation.ValidationResult;
import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.GeometryDetail;
import com.galgothstudio.backend.domain.model.ModelGenerationQualityReport;
import com.galgothstudio.backend.domain.model.TextureDensity;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.template.CanonicalTemplate;
import com.galgothstudio.backend.domain.template.CanonicalTemplateCatalog;
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
import java.util.concurrent.atomic.AtomicBoolean;
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

	/**
	 * Placeholder de arranque de {@link #emptyModelFor} -- SOLO usado antes
	 * de que exista ningún cuboid real (preview de streaming/heartbeat vía
	 * {@code GeometryEngine.apply(model, ops)}, la sobrecarga de 2
	 * argumentos que nunca calcula UV). Ticket 042, Diseño técnico §7 de
	 * `docs/definiciones/galgoth-studio-fase3-textura.md`: el atlas real
	 * del mob YA NO es este valor fijo -- {@code GeometryPlannerService
	 * #applyOperations} lo recalcula desde cero (footprint empaquetado de
	 * los cuboids reales a densidad {@code X1}, potencia de 2 inmediatamente
	 * contenedora) antes de la aplicación final con UV, así que estas
	 * dimensiones nunca llegan a ser el atlas final de ningún mob generado
	 * -- antes del ticket 042 sí lo eran (128×128 hardcodeado), de ahí que
	 * el valor se conserve solo como scratch/placeholder transitorio.
	 */
	private static final TextureDocument DEFAULT_TEXTURE = new TextureDocument(128, 128, null);

	private final MobRepository mobRepository;
	private final ReferenceImageRepository referenceImageRepository;
	private final AssetStorageService assetStorageService;
	private final VisionAnalysisService visionAnalysisService;
	private final GeometryPlannerService geometryPlannerService;
	private final SecondaryGeometryPlanner secondaryGeometryPlanner;
	private final AiJobRepository aiJobRepository;
	private final AiJobEventRepository aiJobEventRepository;
	private final GenerationEventBroadcaster eventBroadcaster;
	private final GenerationCancellationRegistry cancellationRegistry;
	private final ObjectMapper objectMapper;
	private final Executor generationExecutor;
	private final boolean geometryStreamingEnabled;
	private final long heartbeatInitialDelaySeconds;
	private final long heartbeatPeriodSeconds;

	public MobGenerationService(
			MobRepository mobRepository,
			ReferenceImageRepository referenceImageRepository,
			AssetStorageService assetStorageService,
			VisionAnalysisService visionAnalysisService,
			GeometryPlannerService geometryPlannerService,
			SecondaryGeometryPlanner secondaryGeometryPlanner,
			AiJobRepository aiJobRepository,
			AiJobEventRepository aiJobEventRepository,
			GenerationEventBroadcaster eventBroadcaster,
			GenerationCancellationRegistry cancellationRegistry,
			ObjectMapper objectMapper,
			@Qualifier("generationExecutor") Executor generationExecutor,
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
		this.secondaryGeometryPlanner = secondaryGeometryPlanner;
		this.aiJobRepository = aiJobRepository;
		this.aiJobEventRepository = aiJobEventRepository;
		this.eventBroadcaster = eventBroadcaster;
		this.cancellationRegistry = cancellationRegistry;
		this.geometryStreamingEnabled = geometryStreamingEnabled;
		this.heartbeatInitialDelaySeconds = heartbeatInitialDelaySeconds;
		this.heartbeatPeriodSeconds = heartbeatPeriodSeconds;
		this.objectMapper = objectMapper;
		this.generationExecutor = generationExecutor;
	}

	/** Igual que {@link #startGeneration(UUID, GeometryDetail, TextureDensity)} con los defaults (tickets 100/109) -- mantenido por compatibilidad con callers que no eligen detalle geométrico ni densidad. */
	public UUID startGeneration(UUID mobId) {
		return startGeneration(mobId, GeometryDetail.MEDIUM, TextureDensity.MAX);
	}

	/** Igual que {@link #startGeneration(UUID, GeometryDetail, TextureDensity)} con la densidad por defecto (ticket 109). */
	public UUID startGeneration(UUID mobId, GeometryDetail geometryDetail) {
		return startGeneration(mobId, geometryDetail, TextureDensity.MAX);
	}

	/** Preflight síncrono (mob/referencia deben existir, AC implícito del ticket 028 preservado) + creación inmediata de la fila `running` -- el pipeline real se dispara después, en {@code generationExecutor}. */
	public UUID startGeneration(UUID mobId, GeometryDetail geometryDetail, TextureDensity textureDensity) {
		MobEntity mob = mobRepository.findById(mobId).orElseThrow(() -> new MobNotFoundException(mobId));
		ReferenceImageEntity reference = mostRecentReference(mobId);

		AiJobEntity job = newRunningJob(mob.getId(), reference.getId());
		aiJobRepository.save(job);

		GenerationJobContext context = new GenerationJobContext(
				job.getId(), mob.getId(), mob.getProjectId(), mob.getName(), mob.getBaseType(), reference.getId(), reference.getStorageKey(),
				reference.getContentType(), geometryDetail != null ? geometryDetail : GeometryDetail.MEDIUM,
				textureDensity != null ? textureDensity : TextureDensity.MAX);

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

			// Ticket 099 -- la anatomía primaria (esqueleto + volumen esencial)
			// ya NO sale del LLM: PrimaryGeometryGenerator (098) la construye
			// 100% determinista a partir del CanonicalTemplate del baseType
			// (097), sin ninguna llamada de red. Se aplica de una sola vez
			// (instantáneo, sin heartbeat) y se muestra como un único evento
			// de preview -- el LLM entra recién después, para geometría
			// secundaria (ropa/garras/cuernos/jirones), sobre este modelo ya
			// fijado.
			MobProjectModel emptyModel = emptyModelFor(context);
			// GenerationJobContext.baseType() es el String crudo de la BD
			// (ej. "humanoid") -- se convierte al enum vía el mismo ObjectMapper
			// que ya respeta los @JsonProperty de BaseType en el resto del
			// dominio (nunca BaseType.valueOf, que esperaría "HUMANOID").
			CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(objectMapper.convertValue(context.baseType(), BaseType.class));
			PrimaryOperationsResult primary = PrimaryGeometryGenerator.planOperations(template, visionResult.modelIntent());
			logGenerationWarnings(jobId, "anatomía primaria (proporciones)", primary.warnings());
			MobProjectModel primaryModel = GeometryEngine.apply(emptyModel, primary.operations());
			emit(
					jobId, seq, GenerationStage.CREANDO_RIG,
					"Anatomía primaria lista (" + primary.operations().size() + " operaciones).", 35,
					previewSnapshotPayload(primaryModel));
			checkCancellation(jobId);

			// Ticket 100 -- presupuesto elegido en Configuración, traducido a
			// cuboides SECUNDARIOS reales (el extremo superior del rango total
			// menos lo que la anatomía primaria ya cubre) -- nunca solo texto
			// cosmético de prompt.
			int secondaryBudget = context.geometryDetail().secondaryBudget(primaryModel.cuboids().size());

			// Ticket 038 -- hallazgo real (ver ai_job_events de jobs reales en
			// dev, 2026-09-09): esta era la llamada que dejaba la UI "pegada"
			// 70-90s sin ningún evento. Con streaming=true, cada operación
			// real dispara su propio evento acá abajo (via applySecondaryStepAndEmit),
			// en vez de un replay post-hoc instantáneo.
			GeometryPlanExecution secondaryExecution = geometryStreamingEnabled
					? planSecondaryWithStreaming(jobId, seq, visionResult.modelIntent(), primaryModel, secondaryBudget)
					: planSecondaryWithHeartbeat(jobId, seq, visionResult.modelIntent(), primaryModel, secondaryBudget);
			updateJobProviderInfo(jobId, secondaryExecution.providerResponse());
			checkCancellation(jobId);

			emit(jobId, seq, GenerationStage.PREPARANDO_RESULTADO, "Preparando resultado…", 90, null);
			// startingModel=primaryModel (no emptyModel): la geometría
			// secundaria se aplica SOBRE la anatomía primaria ya resuelta,
			// nunca reemplazándola -- GeometryPlannerService.applyOperations
			// es agnóstico de quién produjo las operaciones, mismo cálculo de
			// atlas/UV de siempre, ahora sobre el batch combinado real.
			MobProjectModel finalModel = geometryPlannerService.applyOperations(
					secondaryExecution.operations(), secondaryExecution.providerResponse(), primaryModel, context.textureDensity());
			// Ticket 100, HU-4: el presupuesto es orientativo -- un resultado
			// fuera de rango no falla el job, solo se registra para diagnóstico.
			if (!context.geometryDetail().isWithinBudget(finalModel.cuboids().size())) {
				log.info(
						"Job {}: el conteo final de cuboides ({}) queda fuera del presupuesto orientativo {} [{},{}]",
						jobId, finalModel.cuboids().size(), context.geometryDetail(),
						context.geometryDetail().minTotalCuboids(), context.geometryDetail().maxTotalCuboids());
			}
			checkCancellation(jobId);

			emit(jobId, seq, GenerationStage.VALIDANDO_GEOMETRIA, "Validando compatibilidad con Blockbench/FMM…", 95, null);
			ModelGenerationQualityReport.Metric fmmMetric = validateFmmCompatibilityInformational(jobId, finalModel);
			logQualityReport(jobId, visionResult.modelIntent(), finalModel, fmmMetric);

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
	 * Ticket 099 -- geometría SECUNDARIA solamente (la primaria ya está
	 * fijada en {@code primaryModel}, ver {@link #runPipeline}). Modo
	 * streaming (switch encendido, default): consume la respuesta del
	 * {@link SecondaryGeometryPlanner} incrementalmente y valida/aplica/emite
	 * cada operación real EN CUANTO el modelo la termina de emitir -- una
	 * operación que {@link SecondaryGeometryConstraints} rechaza NUNCA se
	 * aplica ni se muestra en el preview (HU-2b: el job no falla completo,
	 * cada rechazo queda como advertencia logueada, no como algo que el
	 * usuario ve aparecer y luego desaparecer).
	 */
	private GeometryPlanExecution planSecondaryWithStreaming(UUID jobId, AtomicInteger seq, ModelIntent modelIntent, MobProjectModel primaryModel, int secondaryBudget) {
		List<GeometryOperation> collected = new ArrayList<>();
		List<SecondaryGeometryConstraints.Rejection> rejections = new ArrayList<>();
		MobProjectModel[] previewBox = {primaryModel};
		List<SecondaryGeometryPlanner.BoneDescriptor> primaryBones = boneDescriptorsFrom(primaryModel);
		AtomicBoolean firstOperationReceived = new AtomicBoolean(false);
		RawOperationsResult raw;
		try (ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor()) {
			long startNanos = System.nanoTime();
			ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
					() -> {
						if (!firstOperationReceived.get()) {
							long elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000L;
							emit(
									jobId, seq, GenerationStage.GENERANDO_CUBOIDES,
									"Generando geometría secundaria… llevamos " + elapsedSeconds + "s.", 40, null);
						}
					},
					heartbeatInitialDelaySeconds, heartbeatPeriodSeconds, TimeUnit.SECONDS);
			try {
				raw = secondaryGeometryPlanner.planStreaming(modelIntent, primaryBones, secondaryBudget, op -> {
					checkCancellation(jobId);
					firstOperationReceived.set(true);
					SecondaryGeometryConstraints.ValidationResult validated = SecondaryGeometryConstraints.validate(List.of(op), primaryModel);
					if (validated.accepted().isEmpty()) {
						rejections.addAll(validated.rejected());
						return;
					}
					collected.add(op);
					int progressPct = Math.min(89, 40 + collected.size());
					previewBox[0] = applySecondaryStepAndEmit(jobId, seq, primaryModel, collected, previewBox[0], op, progressPct);
				});
			} finally {
				heartbeat.cancel(true);
			}
		}
		logRejections(jobId, rejections);
		return new GeometryPlanExecution(collected, raw.providerResponse());
	}

	/**
	 * Igual que {@link #planSecondaryWithStreaming} pero en modo heartbeat
	 * (switch operativo apagado): espera la respuesta completa, valida el
	 * batch entero de una vez, y reproduce SOLO las operaciones aceptadas.
	 */
	private GeometryPlanExecution planSecondaryWithHeartbeat(UUID jobId, AtomicInteger seq, ModelIntent modelIntent, MobProjectModel primaryModel, int secondaryBudget) {
		List<SecondaryGeometryPlanner.BoneDescriptor> primaryBones = boneDescriptorsFrom(primaryModel);
		RawOperationsResult raw;
		try (ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor()) {
			long startNanos = System.nanoTime();
			ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(
					() -> {
						long elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000L;
						emit(
								jobId, seq, GenerationStage.GENERANDO_CUBOIDES,
								"Generando geometría secundaria… llevamos " + elapsedSeconds + "s.", 40, null);
					},
					heartbeatInitialDelaySeconds, heartbeatPeriodSeconds, TimeUnit.SECONDS);
			try {
				raw = secondaryGeometryPlanner.requestOperations(modelIntent, primaryBones, secondaryBudget);
			} finally {
				heartbeat.cancel(true);
			}
		}

		checkCancellation(jobId);
		SecondaryGeometryConstraints.ValidationResult validated = SecondaryGeometryConstraints.validate(raw.operations(), primaryModel);
		logRejections(jobId, validated.rejected());
		replaySecondaryOpsWithPreview(jobId, seq, validated.accepted(), primaryModel);
		return new GeometryPlanExecution(validated.accepted(), raw.providerResponse());
	}

	/** Reproduce SOLO operaciones ya validadas (nunca vuelve a llamar al proveedor) de a una por vez -- usado en modo heartbeat; en modo streaming, {@link #applySecondaryStepAndEmit} se llama directo a medida que cada operación llega y pasa la validación. */
	private MobProjectModel replaySecondaryOpsWithPreview(UUID jobId, AtomicInteger seq, List<GeometryOperation> acceptedOps, MobProjectModel primaryModel) {
		MobProjectModel previous = primaryModel;
		for (int i = 0; i < acceptedOps.size(); i++) {
			checkCancellation(jobId);
			int progressPct = Math.min(89, 40 + (int) Math.round(45.0 * (i + 1) / Math.max(1, acceptedOps.size())));
			previous = applySecondaryStepAndEmit(jobId, seq, primaryModel, acceptedOps.subList(0, i + 1), previous, acceptedOps.get(i), progressPct);
		}
		return previous;
	}

	/**
	 * Aplica el batch de geometría secundaria visto HASTA AHORA sobre
	 * {@code primaryModel} (nunca sobre un modelo vacío: la anatomía
	 * primaria es la base fija de esta fase) y diferencia contra el preview
	 * anterior -- mismo motivo que la versión previa a este ticket
	 * (`GeometryEngine.apply` asigna ids nuevos cada vez, hay que
	 * reaplicar desde una base estable y dejar que {@link GenerationPreviewDiff}
	 * calcule qué cambió de verdad). Siempre {@code GENERANDO_CUBOIDES}:
	 * geometría secundaria nunca crea bones (ver {@link SecondaryGeometryConstraints}).
	 */
	private MobProjectModel applySecondaryStepAndEmit(
			UUID jobId, AtomicInteger seq, MobProjectModel primaryModel, List<GeometryOperation> acceptedOpsSoFar,
			MobProjectModel previousPreview, GeometryOperation justAdded, int progressPct) {
		MobProjectModel current = GeometryEngine.apply(primaryModel, acceptedOpsSoFar);
		PreviewDelta delta = GenerationPreviewDiff.diff(previousPreview, current);
		if (!delta.isEmpty()) {
			emit(jobId, seq, GenerationStage.GENERANDO_CUBOIDES, describeOperation(justAdded), progressPct, previewOperationsPayload(delta));
		}
		return current;
	}

	/** Bones de {@code primaryModel} descritos para el prompt de {@link SecondaryGeometryPlanner} -- ids REALES (ya resueltos por {@code GeometryEngine}), no tempIds: la geometría secundaria se aplica directo sobre este modelo, nunca se re-mezcla con las operaciones de creación de la anatomía primaria en un batch nuevo. */
	private static List<SecondaryGeometryPlanner.BoneDescriptor> boneDescriptorsFrom(MobProjectModel primaryModel) {
		List<SecondaryGeometryPlanner.BoneDescriptor> descriptors = new ArrayList<>();
		for (var bone : primaryModel.bones()) {
			descriptors.add(new SecondaryGeometryPlanner.BoneDescriptor(bone.id(), bone.name(), bone.pivot()));
		}
		return descriptors;
	}

	/** Ningún rechazo de {@link SecondaryGeometryConstraints} tumba el job (HU-2b) -- se loguean con su razón concreta para diagnóstico; exponerlos en la API como `generationWarnings` estructurados queda para un ticket futuro (candidato natural: 104, `ModelGenerationQualityReport`). */
	private void logRejections(UUID jobId, List<SecondaryGeometryConstraints.Rejection> rejections) {
		for (SecondaryGeometryConstraints.Rejection rejection : rejections) {
			log.info("Job {}: geometría secundaria rechazada -- {}", jobId, rejection.reason());
		}
	}

	/** Advertencias de {@link com.galgothstudio.backend.domain.template.ProportionEstimator} (proporciones clampadas) -- mismo criterio que {@link #logRejections}, nunca se pierden en silencio. */
	private void logGenerationWarnings(UUID jobId, String phase, List<String> warnings) {
		for (String warning : warnings) {
			log.info("Job {}: advertencia de generación ({}) -- {}", jobId, phase, warning);
		}
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
	private ModelGenerationQualityReport.Metric validateFmmCompatibilityInformational(UUID jobId, MobProjectModel finalModel) {
		try {
			String bbmodelJson = BBModelExporterV5.export(finalModel);
			ValidationResult validation = FmmCompatibilityValidator.validate(bbmodelJson);
			if (!validation.pass()) {
				log.info("Job {} generó un modelo con hallazgos de compatibilidad FMM (informativo, no falla el job): {}", jobId, validation.issues());
			}
			// Ticket 104: el mismo resultado alimenta el reporte de calidad --
			// 1 = compatible, 0 = con hallazgos; nunca se vuelve a validar.
			return ModelGenerationQualityReport.Metric.of(validation.pass() ? 1 : 0);
		} catch (RuntimeException e) {
			log.warn(
					"No se pudo correr la validación FMM informativa del job {} durante el pipeline -- se ignora, "
							+ "GenerationResultService la reintenta al servir GET /result.",
					jobId, e);
			// Ticket 104: no se pudo medir -- `unavailable`, nunca un valor inventado.
			return ModelGenerationQualityReport.Metric.unavailable();
		}
	}

	/**
	 * Ticket 104 (HU-5b) -- reporte de calidad puramente diagnóstico: no
	 * bloquea el job, no se expone en UI en v1 (confirmado en el documento
	 * de definición), solo se loguea. Misma deuda declarada que
	 * {@link #logRejections}: exponerlo en la API es alcance de un ticket
	 * futuro, no de este.
	 */
	private void logQualityReport(UUID jobId, ModelIntent intent, MobProjectModel finalModel, ModelGenerationQualityReport.Metric fmm) {
		// El reporte HOY solo existe para loguearse: si el nivel INFO está
		// apagado, calcularlo (y formatearlo) sería trabajo tirado en cada
		// generación. Cuando el ticket futuro lo exponga en la API, este
		// cálculo sale de acá y deja de ser condicional.
		if (!log.isInfoEnabled()) {
			return;
		}
		ModelGenerationQualityReport report = ModelGenerationQualityReport.of(intent, finalModel, fmm);
		log.info("Job {}: reporte de calidad -- {}", jobId, report.describe());
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
