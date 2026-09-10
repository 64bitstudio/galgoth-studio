package com.galgothstudio.backend.project.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.export.BBModelExporterV5;
import com.galgothstudio.backend.domain.export.validation.FmmCompatibilityValidator;
import com.galgothstudio.backend.domain.export.validation.ValidationIssue;
import com.galgothstudio.backend.domain.export.validation.ValidationResult;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.uv.UvLayoutStrategy;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import com.galgothstudio.backend.project.persistence.MobDraftEntity;
import com.galgothstudio.backend.project.persistence.MobDraftRepository;
import com.galgothstudio.backend.project.persistence.MobEntity;
import com.galgothstudio.backend.project.persistence.MobRepository;
import com.galgothstudio.backend.project.persistence.MobRevisionEntity;
import com.galgothstudio.backend.project.persistence.MobRevisionRepository;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pantalla de exportación (ticket 032, HU-19, mockup 11). AC #1 -- el
 * archivo exportado SIEMPRE sale de una fila real de `mob_revisions`,
 * nunca directo del draft en curso: "Guardar y exportar" reutiliza
 * {@code DraftPersistenceService.saveRevision} (020, orquestado desde el
 * frontend como dos llamadas sucesivas -- ver `exportApi.ts`) y solo
 * DESPUÉS de esa revisión existir se llama a {@link #exportBbmodel}, que
 * jamás lee `mob_drafts`. El panel de compatibilidad FMM ({@link #getStatus})
 * se calcula SIEMPRE contra la última revisión guardada, nunca el draft
 * (decisión confirmada explícitamente con el PO): describe un artefacto
 * real ya exportable, nunca un estado hipotético que podría no
 * corresponder a lo que "Exportar última versión guardada" produciría.
 */
@Service
public class MobExportService {

	private final MobRepository mobRepository;
	private final MobRevisionRepository revisionRepository;
	private final MobDraftRepository draftRepository;
	private final UvLayoutStrategy uvLayoutStrategy;
	private final ObjectMapper objectMapper;

	public MobExportService(
			MobRepository mobRepository,
			MobRevisionRepository revisionRepository,
			MobDraftRepository draftRepository,
			// Ticket 041: el exportador queda deliberadamente fuera de
			// UvLayoutSelector (@Primary desde este ticket) -- ver Diseño
			// técnico §2/§3 de `docs/definiciones/galgoth-studio-fase3-textura.md`
			// (Hallazgo A, revertido por el PO) y el ticket 044, que le
			// quitará este parámetro por completo. Qualifier explícito para
			// no heredar el nuevo @Primary por accidente vía autowire-by-type.
			@Qualifier("alphaAutoPackStrategy") UvLayoutStrategy uvLayoutStrategy,
			ObjectMapper objectMapper) {
		this.mobRepository = mobRepository;
		this.revisionRepository = revisionRepository;
		this.draftRepository = draftRepository;
		this.uvLayoutStrategy = uvLayoutStrategy;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public ExportStatusView getStatus(UUID mobId) {
		MobEntity mob = requireMob(mobId);
		boolean hasSavedRevision = mob.getCurrentRevisionNumber() > 0;

		Boolean fmmCompatible = null;
		List<ValidationIssue> fmmIssues = List.of();
		MobProjectModel latestRevisionModel = null;
		if (hasSavedRevision) {
			latestRevisionModel = loadRevisionModel(mob);
			String bbmodelJson = BBModelExporterV5.export(latestRevisionModel, uvLayoutStrategy);
			ValidationResult validation = FmmCompatibilityValidator.validate(bbmodelJson);
			fmmCompatible = validation.pass();
			fmmIssues = validation.issues();
		}

		boolean hasUnsavedChanges = computeHasUnsavedChanges(mobId, hasSavedRevision, latestRevisionModel);
		return new ExportStatusView(mob.getId().toString(), mob.getName(), hasSavedRevision, hasUnsavedChanges, fmmCompatible, fmmIssues);
	}

	/** AC #1/#4 -- exporta la revisión GUARDADA actual, sin tocar `mob_drafts`. 404 (`NO_SAVED_REVISION`) si el mob nunca tuvo ninguna. */
	@Transactional(readOnly = true)
	public ExportedFile exportBbmodel(UUID mobId) {
		MobEntity mob = requireMob(mobId);
		if (mob.getCurrentRevisionNumber() == 0) {
			throw new NoSavedRevisionException(mobId);
		}
		MobProjectModel model = loadRevisionModel(mob);
		String bbmodelJson = BBModelExporterV5.export(model, uvLayoutStrategy);
		return new ExportedFile(safeFilename(mob.getName()), bbmodelJson);
	}

	private boolean computeHasUnsavedChanges(UUID mobId, boolean hasSavedRevision, MobProjectModel latestRevisionModel) {
		Optional<MobDraftEntity> draft = draftRepository.findById(mobId);
		if (draft.isEmpty()) {
			// Sin ningún draft persistido no hay nada que comparar -- el
			// caso de "Guardar" llamado sin autosave previo (mismo edge case
			// ya documentado en 020/031): solo existe la revisión guardada.
			return false;
		}
		if (!hasSavedRevision) {
			return true;
		}
		return !deserialize(draft.get().getModelJson()).equals(latestRevisionModel);
	}

	private MobProjectModel loadRevisionModel(MobEntity mob) {
		MobRevisionEntity revision = revisionRepository
				.findByMobIdAndRevisionNumber(mob.getId(), mob.getCurrentRevisionNumber())
				.orElseThrow(() -> new IllegalStateException(
						"Invariante roto: mobs.current_revision_number=" + mob.getCurrentRevisionNumber()
								+ " pero no existe esa fila en mob_revisions para el mob " + mob.getId()));
		return deserialize(revision.getModelJson());
	}

	private MobEntity requireMob(UUID mobId) {
		return mobRepository.findById(mobId).orElseThrow(() -> new MobNotFoundException(mobId));
	}

	private MobProjectModel deserialize(String json) {
		try {
			return objectMapper.readValue(json, MobProjectModel.class);
		} catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}

	static String safeFilename(String mobName) {
		String sanitized = mobName == null ? "" : mobName.trim().replaceAll("[^a-zA-Z0-9_-]+", "_");
		return (sanitized.isEmpty() ? "mob" : sanitized) + ".bbmodel";
	}

}
