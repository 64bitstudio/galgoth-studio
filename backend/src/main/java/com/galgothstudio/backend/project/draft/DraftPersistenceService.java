package com.galgothstudio.backend.project.draft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.modelvalidation.MobProjectModelValidator;
import com.galgothstudio.backend.project.persistence.MobDraftEntity;
import com.galgothstudio.backend.project.persistence.MobDraftRepository;
import com.galgothstudio.backend.project.persistence.MobEntity;
import com.galgothstudio.backend.project.persistence.MobRepository;
import com.galgothstudio.backend.project.persistence.MobRevisionEntity;
import com.galgothstudio.backend.project.persistence.MobRevisionRepository;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementa el ciclo de vida Command → Draft → Revision de HU-08/HU-09/
 * HU-22 (ticket 020, `docs/definiciones/galgoth-studio-mvp.md` Diseño
 * técnico §4): el autosave persiste `mob_drafts` SOLO en cambio material
 * (dirty-check por igualdad estructural de {@link MobProjectModel}, que
 * ya tiene equals de valor correcto -- ver Vec3/Vec4, ticket 004) y
 * NUNCA crea una revisión; `Guardar` es la única acción manual que sí
 * crea una fila inmutable en `mob_revisions`, tras validar invariantes.
 *
 * Guardar NO toca `mob_drafts` -- son dos propósitos independientes
 * (resumición vs. historial inmutable) y ningún AC de HU-09/HU-22 pide
 * mantenerlos sincronizados; el siguiente autosave los realinea solo.
 */
@Service
public class DraftPersistenceService {

	private final MobRepository mobRepository;
	private final MobDraftRepository draftRepository;
	private final MobRevisionRepository revisionRepository;
	private final MobProjectModelValidator validator;
	private final ObjectMapper objectMapper;

	public DraftPersistenceService(
			MobRepository mobRepository,
			MobDraftRepository draftRepository,
			MobRevisionRepository revisionRepository,
			MobProjectModelValidator validator,
			ObjectMapper objectMapper) {
		this.mobRepository = mobRepository;
		this.draftRepository = draftRepository;
		this.revisionRepository = revisionRepository;
		this.validator = validator;
		this.objectMapper = objectMapper;
	}

	@Transactional(readOnly = true)
	public DraftView getDraft(UUID mobId) {
		requireMob(mobId);
		MobDraftEntity draft = draftRepository.findById(mobId).orElseThrow(() -> new DraftNotFoundException(mobId));
		return new DraftView(mobId.toString(), draft.getDraftVersion(), deserialize(draft.getModelJson()), draft.getUpdatedAt());
	}

	@Transactional
	public AutosaveResponse autosave(UUID mobId, MobProjectModel model) {
		requireMob(mobId);
		Optional<MobDraftEntity> existing = draftRepository.findById(mobId);
		if (existing.isEmpty()) {
			// Primer autosave de este mob -- no hay nada previo con qué
			// comparar, así que siempre es un cambio (de "no existe" a "existe").
			MobDraftEntity created = new MobDraftEntity(mobId, serialize(model), 1, Instant.now());
			draftRepository.save(created);
			return new AutosaveResponse(true, created.getDraftVersion(), created.getUpdatedAt());
		}

		MobDraftEntity draft = existing.get();
		if (deserialize(draft.getModelJson()).equals(model)) {
			// AC #2: contenido idéntico -- no se escribe NADA, ni siquiera updated_at.
			return new AutosaveResponse(false, draft.getDraftVersion(), draft.getUpdatedAt());
		}

		draft.setModelJson(serialize(model));
		draft.setDraftVersion(draft.getDraftVersion() + 1);
		draft.setUpdatedAt(Instant.now());
		draftRepository.save(draft);
		return new AutosaveResponse(true, draft.getDraftVersion(), draft.getUpdatedAt());
	}

	@Transactional
	public SaveRevisionResponse saveRevision(UUID mobId, MobProjectModel model) {
		MobEntity mob = requireMob(mobId);

		List<String> errors = validator.validate(model);
		if (!errors.isEmpty()) {
			throw new InvalidDraftException(errors);
		}

		if (mob.getCurrentRevisionNumber() > 0) {
			MobRevisionEntity latest = revisionRepository
					.findByMobIdAndRevisionNumber(mobId, mob.getCurrentRevisionNumber())
					.orElseThrow(() -> new IllegalStateException(
							"Invariante roto: mobs.current_revision_number=" + mob.getCurrentRevisionNumber()
									+ " pero no existe esa fila en mob_revisions para el mob " + mobId));
			if (deserialize(latest.getModelJson()).equals(model)) {
				// AC #5: sin cambios desde la última revisión -- no se duplica.
				return new SaveRevisionResponse(false, mob.getCurrentRevisionNumber(), "Sin cambios desde la última revisión guardada.");
			}
		}

		int newRevisionNumber = mob.getCurrentRevisionNumber() + 1;
		MobRevisionEntity revision =
				new MobRevisionEntity(UUID.randomUUID(), mobId, newRevisionNumber, serialize(model), "user", Instant.now());
		revisionRepository.save(revision);

		mob.setCurrentRevisionNumber(newRevisionNumber);
		mob.setUpdatedAt(Instant.now());
		mobRepository.save(mob);

		return new SaveRevisionResponse(true, newRevisionNumber, null);
	}

	private MobEntity requireMob(UUID mobId) {
		return mobRepository.findById(mobId).orElseThrow(() -> new MobNotFoundException(mobId));
	}

	private String serialize(MobProjectModel model) {
		try {
			return objectMapper.writeValueAsString(model);
		} catch (JsonProcessingException e) {
			// MobProjectModel es un record con módulos Jackson ya probados
			// (Vec3/Vec4, ticket 004) -- si esto falla es un bug real, no
			// una condición esperada de negocio.
			throw new UncheckedIOException(e);
		}
	}

	private MobProjectModel deserialize(String json) {
		try {
			return objectMapper.readValue(json, MobProjectModel.class);
		} catch (JsonProcessingException e) {
			throw new UncheckedIOException(e);
		}
	}

}
