package com.galgothstudio.backend.aiorchestrator.edit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.JobNotCompletedException;
import com.galgothstudio.backend.aiorchestrator.JobNotFoundException;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.planner.InvalidGeometryProposalException;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationPreviewDiff;
import com.galgothstudio.backend.aiorchestrator.progress.PreviewDelta;
import com.galgothstudio.backend.aiorchestrator.provider.AiProviderResponse;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.project.draft.ApplyGenerationResponse;
import com.galgothstudio.backend.project.draft.DraftPersistenceService;
import com.galgothstudio.backend.project.draft.DraftView;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import com.galgothstudio.backend.project.persistence.MobEntity;
import com.galgothstudio.backend.project.persistence.MobRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Orquesta la edición conversacional por IA (ticket 031, HU-17/HU-18):
 * {@link #requestPlan} genera un plan (síncrono, una sola llamada al
 * `StructuredReasoningProvider` -- a diferencia del pipeline de
 * generación 028/029, acá no hace falta SSE, es una sola llamada
 * rápida) sin tocar `mob_drafts`/`mob_revisions`; {@link #applyEdit}
 * confirma la propuesta, verificando que el draft/revisión base no
 * hayan avanzado desde que se generó (AC #5).
 */
@Service
public class AiEditService {

	private static final String JOB_TYPE_EDIT = "edit";
	private static final String STATUS_COMPLETED = "completed";
	private static final String STATUS_FAILED = "failed";

	private final MobRepository mobRepository;
	private final DraftPersistenceService draftPersistenceService;
	private final AiJobRepository aiJobRepository;
	private final AiGeometryEditPlannerService plannerService;
	private final ObjectMapper objectMapper;

	public AiEditService(
			MobRepository mobRepository,
			DraftPersistenceService draftPersistenceService,
			AiJobRepository aiJobRepository,
			AiGeometryEditPlannerService plannerService,
			ObjectMapper objectMapper) {
		this.mobRepository = mobRepository;
		this.draftPersistenceService = draftPersistenceService;
		this.aiJobRepository = aiJobRepository;
		this.plannerService = plannerService;
		this.objectMapper = objectMapper;
	}

	/**
	 * AC #1/#2: genera el plan contra el draft ACTUAL -- `base_revision_number`/
	 * `base_draft_version` se capturan ANTES de llamar a la IA, así el
	 * job registra exactamente el estado contra el que se generó (AC #5
	 * los verifica después, en {@link #applyEdit}). Ni el draft ni las
	 * revisiones se tocan acá -- solo `ai_jobs` (AC #6: un fallo de la
	 * IA nunca deja el modelo real con cambios).
	 */
	public EditGeometryPlanView requestPlan(UUID mobId, String instruction) {
		MobEntity mob = mobRepository.findById(mobId).orElseThrow(() -> new MobNotFoundException(mobId));
		if (mob.getCurrentRevisionNumber() == 0) {
			// `fk_ai_jobs_base_revision` (003) exige que base_revision_number
			// apunte a una fila real de mob_revisions -- revision_number=0
			// nunca existe (arrancan en 1). Falla rápido, antes de gastar una
			// llamada real a la IA que de todas formas no se podría persistir.
			throw new NoBaseRevisionException(mobId);
		}
		DraftView draftView = draftPersistenceService.getDraft(mobId); // DraftNotFoundException si el mob no tiene ningún draft todavía -- nada que editar
		MobProjectModel before = draftView.model();

		EditPlanResult result;
		try {
			result = plannerService.plan(before, instruction);
		} catch (InvalidGeometryProposalException e) {
			persistJob(mob, draftView, e.providerResponse(), STATUS_FAILED, null, e.getMessage());
			throw e;
		}

		AiJobEntity job = persistJob(mob, draftView, result.providerResponse(), STATUS_COMPLETED, result.afterModel(), null);

		List<ChangedElement> changed = computeChangedElements(before, result.afterModel());
		return new EditGeometryPlanView(
				job.getId(),
				result.summary(),
				before.cuboids().size(),
				before.bones().size(),
				result.afterModel().cuboids().size(),
				result.afterModel().bones().size(),
				changed,
				before,
				result.afterModel());
	}

	/** AC #3/#4/#5: aplica una propuesta YA generada -- 409 (no aplica nada) si el draft/revisión base avanzaron desde entonces. */
	public ApplyGenerationResponse applyEdit(UUID jobId) {
		AiJobEntity job = aiJobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId));
		if (!JOB_TYPE_EDIT.equals(job.getJobType()) || !STATUS_COMPLETED.equals(job.getStatus())) {
			throw new JobNotCompletedException(jobId, job.getStatus());
		}

		DraftView currentDraft = draftPersistenceService.getDraft(job.getMobId());
		MobEntity mob = mobRepository.findById(job.getMobId()).orElseThrow(() -> new MobNotFoundException(job.getMobId()));
		if (mob.getCurrentRevisionNumber() != job.getBaseRevisionNumber() || currentDraft.draftVersion() != job.getBaseDraftVersion()) {
			throw new StaleEditBaseException(jobId);
		}

		MobProjectModel afterModel = deserialize(job.getProposalJson());
		return draftPersistenceService.applyGenerationProposal(job.getMobId(), afterModel);
	}

	private List<ChangedElement> computeChangedElements(MobProjectModel before, MobProjectModel after) {
		PreviewDelta delta = GenerationPreviewDiff.diff(before, after);
		Set<String> beforeBoneIds = before.bones().stream().map(Bone::id).collect(Collectors.toSet());
		Set<String> beforeCuboidIds = before.cuboids().stream().map(Cuboid::id).collect(Collectors.toSet());
		Map<String, Cuboid> beforeCuboidsById = before.cuboids().stream().collect(Collectors.toMap(Cuboid::id, c -> c));

		List<ChangedElement> result = new ArrayList<>();
		for (Bone bone : delta.addedOrUpdatedBones()) {
			result.add(new ChangedElement("bone", bone.id(), bone.name(), beforeBoneIds.contains(bone.id()) ? "modified" : "added"));
		}
		for (Cuboid cuboid : delta.addedOrUpdatedCuboids()) {
			result.add(new ChangedElement("cuboid", cuboid.id(), cuboid.name(), beforeCuboidIds.contains(cuboid.id()) ? "modified" : "added"));
		}
		for (String removedId : delta.removedCuboidIds()) {
			Cuboid removed = beforeCuboidsById.get(removedId);
			result.add(new ChangedElement("cuboid", removedId, removed != null ? removed.name() : removedId, "removed"));
		}
		return result;
	}

	private AiJobEntity persistJob(
			MobEntity mob, DraftView draftView, AiProviderResponse providerResponse, String status, MobProjectModel proposal, String error) {
		Instant now = Instant.now();
		AiJobEntity job = new AiJobEntity(UUID.randomUUID());
		job.setMobId(mob.getId());
		job.setJobType(JOB_TYPE_EDIT);
		job.setStatus(status);
		job.setProvider(providerResponse.provider());
		job.setModel(providerResponse.model());
		job.setPromptVersion(providerResponse.promptVersion());
		job.setSchemaVersion(providerResponse.schemaVersion());
		job.setReferenceIds(writeJson(List.of()));
		// job_type='edit' -- AMBOS requeridos por el CHECK de ai_jobs (003), a diferencia de 'generate'.
		job.setBaseRevisionNumber(mob.getCurrentRevisionNumber());
		job.setBaseDraftVersion(draftView.draftVersion());
		job.setProposalJson(proposal == null ? null : writeJson(proposal));
		job.setError(error);
		job.setCreatedAt(now);
		job.setStartedAt(now);
		job.setFinishedAt(now);
		return aiJobRepository.save(job);
	}

	private MobProjectModel deserialize(String json) {
		try {
			return objectMapper.readValue(json, MobProjectModel.class);
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo deserializar un proposal_jsonb ya validado por el propio pipeline.", e);
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo serializar un valor de dominio ya validado a JSON.", e);
		}
	}

}
