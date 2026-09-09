package com.galgothstudio.backend.aiorchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.api.GenerationResultView;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.domain.export.BBModelExporterV5;
import com.galgothstudio.backend.domain.export.validation.FmmCompatibilityValidator;
import com.galgothstudio.backend.domain.export.validation.ValidationResult;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.uv.UvLayoutStrategy;
import com.galgothstudio.backend.project.draft.ApplyGenerationResponse;
import com.galgothstudio.backend.project.draft.DraftPersistenceService;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import com.galgothstudio.backend.project.persistence.MobEntity;
import com.galgothstudio.backend.project.persistence.MobRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Lado "leer resultado"/"aceptar propuesta" del pipeline de generación
 * (ticket 030, HU-12) -- deliberadamente separado de
 * {@link MobGenerationService} (que orquesta el pipeline EN SÍ, 028/029):
 * acá no hay ninguna llamada a un proveedor de IA, solo lectura de un
 * job ya terminado y, en {@link #apply}, la creación real de la
 * revisión/draft (delegada a {@link DraftPersistenceService}, mismo
 * mecanismo que "Guardar", 020).
 */
@Service
public class GenerationResultService {

	private final AiJobRepository aiJobRepository;
	private final MobRepository mobRepository;
	private final DraftPersistenceService draftPersistenceService;
	private final UvLayoutStrategy uvLayoutStrategy;
	private final ObjectMapper objectMapper;

	public GenerationResultService(
			AiJobRepository aiJobRepository,
			MobRepository mobRepository,
			DraftPersistenceService draftPersistenceService,
			UvLayoutStrategy uvLayoutStrategy,
			ObjectMapper objectMapper) {
		this.aiJobRepository = aiJobRepository;
		this.mobRepository = mobRepository;
		this.draftPersistenceService = draftPersistenceService;
		this.uvLayoutStrategy = uvLayoutStrategy;
		this.objectMapper = objectMapper;
	}

	public GenerationResultView getResult(UUID jobId) {
		AiJobEntity job = requireCompletedJob(jobId);
		MobProjectModel model = deserialize(job.getProposalJson());
		MobEntity mob = mobRepository.findById(job.getMobId()).orElseThrow(() -> new MobNotFoundException(job.getMobId()));

		// El estado REAL de compatibilidad FMM (013), no un estimado -- exporta
		// la propuesta tal cual quedaría si se aceptara, y le corre encima el
		// mismo validador que el exportador productivo usa como último checkpoint.
		String bbmodelJson = BBModelExporterV5.export(model, uvLayoutStrategy);
		ValidationResult validation = FmmCompatibilityValidator.validate(bbmodelJson);

		return new GenerationResultView(
				job.getId(),
				mob.getId(),
				mob.getName(),
				model.cuboids().size(),
				model.bones().size(),
				model.texture().width(),
				model.texture().height(),
				validation.pass(),
				validation.issues());
	}

	/** "Usar este modelo" -- delega en {@link DraftPersistenceService#applyGenerationProposal}, nunca vuelve a ejecutar el pipeline de IA. */
	public ApplyGenerationResponse apply(UUID jobId) {
		AiJobEntity job = requireCompletedJob(jobId);
		MobProjectModel model = deserialize(job.getProposalJson());
		return draftPersistenceService.applyGenerationProposal(job.getMobId(), model);
	}

	private AiJobEntity requireCompletedJob(UUID jobId) {
		AiJobEntity job = aiJobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
		if (!"completed".equals(job.getStatus())) {
			throw new JobNotCompletedException(jobId, job.getStatus());
		}
		return job;
	}

	private MobProjectModel deserialize(String proposalJson) {
		try {
			return objectMapper.readValue(proposalJson, MobProjectModel.class);
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo deserializar un proposal_jsonb ya validado por el propio pipeline.", e);
		}
	}

}
