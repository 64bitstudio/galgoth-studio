package com.galgothstudio.backend.project.export;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.asset.AssetStorageService;
import com.galgothstudio.backend.domain.export.BBModelExporterV5;
import com.galgothstudio.backend.domain.export.validation.FmmCompatibilityValidator;
import com.galgothstudio.backend.domain.export.validation.ValidationIssue;
import com.galgothstudio.backend.domain.export.validation.ValidationResult;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.uv.LegacyUvNormalizationService;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 *
 * <p>Ticket 056 (HU-43) -- segundo gap real detectado y cerrado con VoBo
 * explícito del PO: `BBModelExporterV5`/`V4` nunca leían
 * `model.texture().storageKey()`, así que TODO export embebía siempre el
 * checkerboard placeholder (011), incluso con textura real ya persistida
 * (045/046-054). Este servicio es ahora el ÚNICO punto que resuelve esos
 * bytes reales vía {@link AssetStorageService#get}, y se los pasa al
 * exportador como parámetro -- el exportador SIGUE sin ninguna
 * dependencia de infraestructura (garantía ya defendida por el PO,
 * "Hallazgo A revertido", Diseño técnico §3), mismo patrón que
 * {@link LegacyUvNormalizationService}: resolución explícita del CALLER,
 * nunca dentro del exportador.
 */
@Service
public class MobExportService {

	private static final Logger log = LoggerFactory.getLogger(MobExportService.class);

	private final MobRepository mobRepository;
	private final MobRevisionRepository revisionRepository;
	private final MobDraftRepository draftRepository;
	private final LegacyUvNormalizationService legacyUvNormalizationService;
	private final AssetStorageService assetStorageService;
	private final ObjectMapper objectMapper;

	public MobExportService(
			MobRepository mobRepository,
			MobRevisionRepository revisionRepository,
			MobDraftRepository draftRepository,
			// Ticket 044: único caller real de exportación desde una Revision
			// persistida -- normaliza la UV legacy (Diseño técnico §3 de
			// `docs/definiciones/galgoth-studio-fase3-textura.md`) ANTES de
			// llamar al exportador, que desde este ticket ya no acepta ningún
			// UvLayoutStrategy (ni lo necesita: nunca recomputa nada).
			LegacyUvNormalizationService legacyUvNormalizationService,
			AssetStorageService assetStorageService,
			ObjectMapper objectMapper) {
		this.mobRepository = mobRepository;
		this.revisionRepository = revisionRepository;
		this.draftRepository = draftRepository;
		this.legacyUvNormalizationService = legacyUvNormalizationService;
		this.assetStorageService = assetStorageService;
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
			MobProjectModel normalized = legacyUvNormalizationService.normalizeIfSafe(latestRevisionModel);
			String bbmodelJson = BBModelExporterV5.export(normalized, resolveRealTextureBytes(normalized));
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
		MobProjectModel normalized = legacyUvNormalizationService.normalizeIfSafe(model);
		String bbmodelJson = BBModelExporterV5.export(normalized, resolveRealTextureBytes(normalized));
		return new ExportedFile(safeFilename(mob.getName()), bbmodelJson);
	}

	/**
	 * Ticket 056 (HU-43) -- `null` (nunca lanza) si el mob no tiene textura
	 * real todavía (`storageKey == null`, el caso de siempre para un mob
	 * sin ninguna región pintada -- AC #2 exige que ESE caso siga usando el
	 * placeholder tal cual). También `null` si `storageKey` está seteado
	 * pero los bytes ya no existen en el storage: invariante roto en teoría
	 * (`DraftPersistenceService.saveRevision` ya verifica esto en
	 * profundidad ANTES de escribir la Revision, ticket 045), pero el
	 * export nunca debe romperse en caliente por eso -- se loguea como
	 * WARN (nunca silencioso) y se cae al placeholder en vez de fallar
	 * un export que de otro modo sería válido.
	 */
	private byte[] resolveRealTextureBytes(MobProjectModel model) {
		TextureDocument texture = model.texture();
		String storageKey = texture != null ? texture.storageKey() : null;
		if (storageKey == null) {
			return null;
		}
		Optional<byte[]> bytes = assetStorageService.get(storageKey);
		if (bytes.isEmpty()) {
			log.warn(
					"Revision referencia storageKey '{}' que ya no existe en el storage -- exportando con placeholder en su lugar (invariante roto: DraftPersistenceService.saveRevision debería haber impedido esto).",
					storageKey);
			return null;
		}
		return bytes.get();
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
