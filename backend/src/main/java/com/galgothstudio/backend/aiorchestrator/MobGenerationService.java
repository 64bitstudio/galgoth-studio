package com.galgothstudio.backend.aiorchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.planner.GeometryPlanResult;
import com.galgothstudio.backend.aiorchestrator.planner.GeometryPlannerService;
import com.galgothstudio.backend.aiorchestrator.planner.InvalidGeometryProposalException;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.aiorchestrator.vision.InvalidModelIntentException;
import com.galgothstudio.backend.aiorchestrator.vision.ModelIntentAnalysisResult;
import com.galgothstudio.backend.aiorchestrator.vision.VisionAnalysisService;
import com.galgothstudio.backend.asset.AssetStorageService;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta el pipeline completo de generación (ticket 028, master prompt
 * §5 pasos 1-3): Vision → `ModelIntent` validado →
 * Geometry planner → `MobProjectModel` propuesto, persistiendo el
 * resultado en `ai_jobs` (AC #4). Deliberadamente SÍNCRONO -- el job
 * corre y termina dentro de esta misma llamada; el ticket 029 (SSE de
 * progreso) es quien lo vuelve asíncrono con eventos de preview en vivo,
 * este ticket no anticipa esa infraestructura.
 *
 * <p><b>Limitación real, documentada a propósito (no un bug silencioso):</b>
 * un fallo de RED (`AiProviderException`, ej. la API de Anthropic no
 * responde) nunca llega a persistir una fila en `ai_jobs` -- en ese punto
 * no hay ninguna {@link AiProviderResponse} real con provider/modelo que
 * registrar, y esas columnas son `NOT NULL`. Solo los fallos de
 * VALIDACIÓN (`ModelIntent`/geometría inválidos, AC #1/#2), que sí
 * cargan una respuesta real del proveedor, generan una fila `failed`. El
 * ticket 029, que crea el job ANTES de llamar a ningún proveedor (para
 * poder emitir eventos de progreso desde el principio), es el punto
 * natural para cerrar esta brecha -- documentado explícitamente en vez
 * de fingir una fila con datos inventados.
 */
@Service
public class MobGenerationService {

	private static final String JOB_TYPE_GENERATE = "generate";
	private static final String STATUS_COMPLETED = "completed";
	private static final String STATUS_FAILED = "failed";
	/**
	 * Hallazgo real de verificación en vivo (ticket 028): un atlas de
	 * 64x64 (default histórico de fixtures de test) no alcanza para un
	 * rig humanoide real generado por IA -- `UvAtlasOverflowException`
	 * pidió al menos 64x70 para la primera propuesta real contra
	 * `carcomido_reference.png`. 128x128 es la "resolución de textura"
	 * recomendada por defecto en el wizard (ticket 027, mockup 02) -- se
	 * usa el mismo valor acá para que el atlas real generado nunca
	 * dependa de que la propuesta de la IA sea mínima. Si algún día no
	 * alcanza, `UvAtlasOverflowException` lo va a decir explícito (nunca
	 * crece en silencio, 006) -- no una adivinanza silenciosa.
	 */
	private static final TextureDocument DEFAULT_TEXTURE = new TextureDocument(128, 128, null);

	private final MobRepository mobRepository;
	private final ReferenceImageRepository referenceImageRepository;
	private final AssetStorageService assetStorageService;
	private final VisionAnalysisService visionAnalysisService;
	private final GeometryPlannerService geometryPlannerService;
	private final AiJobRepository aiJobRepository;
	private final ObjectMapper objectMapper;

	public MobGenerationService(
			MobRepository mobRepository,
			ReferenceImageRepository referenceImageRepository,
			AssetStorageService assetStorageService,
			VisionAnalysisService visionAnalysisService,
			GeometryPlannerService geometryPlannerService,
			AiJobRepository aiJobRepository,
			ObjectMapper objectMapper) {
		this.mobRepository = mobRepository;
		this.referenceImageRepository = referenceImageRepository;
		this.assetStorageService = assetStorageService;
		this.visionAnalysisService = visionAnalysisService;
		this.geometryPlannerService = geometryPlannerService;
		this.aiJobRepository = aiJobRepository;
		this.objectMapper = objectMapper;
	}

	/**
	 * {@code noRollbackFor} es necesario, no cosmético: sin él, cuando
	 * `persistFailedJob` escribe la fila `failed` y el método RE-LANZA la
	 * excepción a continuación (para que el caller se entere del fallo),
	 * Spring marca la transacción completa como rollback-only por la
	 * excepción que sale del método -- deshaciendo la propia fila
	 * `failed` que se acaba de guardar (confirmado real: sin este ajuste,
	 * los tests que verifican la fila `failed` vía JDBC crudo la
	 * encontraban vacía). No hay ninguna otra escritura previa en esta
	 * transacción que proteger de un rollback parcial, así que
	 * `noRollbackFor` es seguro acá.
	 */
	@Transactional(noRollbackFor = {InvalidModelIntentException.class, InvalidGeometryProposalException.class})
	public GenerationResult generate(UUID mobId) {
		MobEntity mob = mobRepository.findById(mobId).orElseThrow(() -> new MobNotFoundException(mobId));
		ReferenceImageEntity reference = mostRecentReference(mobId);
		byte[] imageBytes = assetStorageService
				.get(reference.getStorageKey())
				.orElseThrow(() -> new IllegalStateException("La imagen de referencia '" + reference.getId() + "' no está en el storage."));

		ModelIntentAnalysisResult visionResult;
		try {
			visionResult = visionAnalysisService.analyze(imageBytes, reference.getContentType(), mob.getBaseType());
		} catch (InvalidModelIntentException e) {
			persistFailedJob(mob, reference, e.providerResponse(), e.getMessage());
			throw e;
		}

		GeometryPlanResult planResult;
		try {
			planResult = geometryPlannerService.plan(visionResult.modelIntent(), emptyModelFor(mob));
		} catch (InvalidGeometryProposalException e) {
			persistFailedJob(mob, reference, e.providerResponse(), e.getMessage());
			throw e;
		}

		AiJobEntity job = persistCompletedJob(mob, reference, planResult);
		return new GenerationResult(job.getId(), planResult.model());
	}

	private ReferenceImageEntity mostRecentReference(UUID mobId) {
		List<ReferenceImageEntity> references = referenceImageRepository.findByMobIdOrderByCreatedAtAsc(mobId);
		if (references.isEmpty()) {
			throw new NoReferenceImageException(mobId);
		}
		return references.getLast();
	}

	/** Modelo vacío (sin bones/cuboids) desde el que arranca el Geometry planner -- HU-10 AC #3: nada persistido todavía, el mob real conserva `current_revision_number=0` hasta "Usar este modelo" (030). */
	private MobProjectModel emptyModelFor(MobEntity mob) {
		BaseType baseType = objectMapper.convertValue(mob.getBaseType(), BaseType.class);
		return new MobProjectModel(
				mob.getId().toString(),
				mob.getProjectId().toString(),
				mob.getName(),
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

	private AiJobEntity persistCompletedJob(MobEntity mob, ReferenceImageEntity reference, GeometryPlanResult planResult) {
		AiJobEntity job = newJob(mob, reference, planResult.providerResponse());
		job.setStatus(STATUS_COMPLETED);
		job.setProposalJson(writeJson(planResult.model()));
		job.setFinishedAt(Instant.now());
		return aiJobRepository.save(job);
	}

	private void persistFailedJob(MobEntity mob, ReferenceImageEntity reference, AiProviderResponse providerResponse, String errorMessage) {
		AiJobEntity job = newJob(mob, reference, providerResponse);
		job.setStatus(STATUS_FAILED);
		job.setError(errorMessage);
		job.setFinishedAt(Instant.now());
		aiJobRepository.save(job);
	}

	private AiJobEntity newJob(MobEntity mob, ReferenceImageEntity reference, AiProviderResponse providerResponse) {
		Instant now = Instant.now();
		AiJobEntity job = new AiJobEntity(UUID.randomUUID());
		job.setMobId(mob.getId());
		job.setJobType(JOB_TYPE_GENERATE);
		job.setProvider(providerResponse.provider());
		job.setModel(providerResponse.model());
		job.setPromptVersion(providerResponse.promptVersion());
		job.setSchemaVersion(providerResponse.schemaVersion());
		job.setReferenceIds(writeJson(List.of(reference.getId().toString())));
		// job_type='generate' -- sin revisión/draft previos, ver el CHECK de ai_jobs (ticket 003).
		job.setBaseRevisionNumber(null);
		job.setBaseDraftVersion(null);
		job.setCreatedAt(now);
		job.setStartedAt(now);
		return job;
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo serializar un valor de dominio ya validado a JSON.", e);
		}
	}

}
