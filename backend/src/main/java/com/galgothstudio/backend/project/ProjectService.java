package com.galgothstudio.backend.project;

import com.galgothstudio.backend.project.persistence.MobDraftEntity;
import com.galgothstudio.backend.project.persistence.MobDraftRepository;
import com.galgothstudio.backend.project.persistence.MobEntity;
import com.galgothstudio.backend.project.persistence.MobRepository;
import com.galgothstudio.backend.project.persistence.MobRevisionEntity;
import com.galgothstudio.backend.project.persistence.MobRevisionRepository;
import com.galgothstudio.backend.project.persistence.ProjectEntity;
import com.galgothstudio.backend.project.persistence.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de proyectos (ticket 021, HU-01/HU-02). "Delete" es soft-delete
 * (`projects.deleted_at`, ya en el esquema desde el ticket 003) -- un
 * proyecto eliminado deja de aparecer en cualquier consulta de esta
 * clase, tratado igual que "no existe" (`ProjectNotFoundException`).
 *
 * "Duplicate" es una copia PROFUNDA -- VoBo explícito del Product
 * Owner: copia el proyecto y TODOS sus mobs, con el historial completo
 * de `mob_revisions` y el `mob_drafts` actual de cada uno (si tiene).
 * Limitación conocida y documentada (Hecho del ticket): el campo
 * `mobId` DENTRO del JSON de cada revisión/draft copiado NO se
 * reescribe al nuevo id -- es un campo puramente informativo dentro del
 * propio documento (ningún código de negocio lo cruza contra la fila
 * real, ver ticket 020), así que reescribirlo agregaría complejidad sin
 * ningún beneficio funcional real.
 */
@Service
public class ProjectService {

	private static final String COPY_SUFFIX = " (copia)";

	private final ProjectRepository projectRepository;
	private final MobRepository mobRepository;
	private final MobDraftRepository draftRepository;
	private final MobRevisionRepository revisionRepository;

	public ProjectService(
			ProjectRepository projectRepository,
			MobRepository mobRepository,
			MobDraftRepository draftRepository,
			MobRevisionRepository revisionRepository) {
		this.projectRepository = projectRepository;
		this.mobRepository = mobRepository;
		this.draftRepository = draftRepository;
		this.revisionRepository = revisionRepository;
	}

	@Transactional
	public ProjectDetail create(String name) {
		String validName = requireValidName(name);
		Instant now = Instant.now();
		ProjectEntity project = new ProjectEntity(UUID.randomUUID(), validName, null, now, now);
		projectRepository.save(project);
		return toDetail(project, 0);
	}

	@Transactional(readOnly = true)
	public List<ProjectSummary> list() {
		return projectRepository.findByDeletedAtIsNullOrderByUpdatedAtDesc().stream().map(this::toSummary).toList();
	}

	@Transactional(readOnly = true)
	public ProjectDetail get(UUID projectId) {
		ProjectEntity project = requireProject(projectId);
		return toDetail(project, (int) mobRepository.countByProjectIdAndDeletedAtIsNull(projectId));
	}

	/** Ticket 073 -- `description` siempre explícita (nunca ambigua entre "ausente" y "null"), ver docstring de `RenameProjectRequest`. */
	@Transactional
	public ProjectDetail rename(UUID projectId, String newName, String description) {
		ProjectEntity project = requireProject(projectId);
		project.setName(requireValidName(newName));
		project.setDescription(normalizeDescription(description));
		project.setUpdatedAt(Instant.now());
		projectRepository.save(project);
		return toDetail(project, (int) mobRepository.countByProjectIdAndDeletedAtIsNull(projectId));
	}

	@Transactional
	public void softDelete(UUID projectId) {
		ProjectEntity project = requireProject(projectId);
		project.setDeletedAt(Instant.now());
		projectRepository.save(project);
	}

	@Transactional
	public ProjectDetail duplicate(UUID projectId) {
		ProjectEntity original = requireProject(projectId);
		Instant now = Instant.now();
		ProjectEntity copy = new ProjectEntity(UUID.randomUUID(), original.getName() + COPY_SUFFIX, original.getOwnerRef(), now, now);
		copy.setDescription(original.getDescription()); // ticket 073 -- copia profunda: la descripción también se copia.
		projectRepository.save(copy);

		List<MobEntity> mobs = mobRepository.findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId);
		for (MobEntity mob : mobs) {
			duplicateMob(mob, copy.getId(), now);
		}

		return toDetail(copy, mobs.size());
	}

	private void duplicateMob(MobEntity original, UUID newProjectId, Instant now) {
		UUID newMobId = UUID.randomUUID();
		MobEntity copy = new MobEntity(newMobId);
		copy.setProjectId(newProjectId);
		copy.setName(original.getName());
		copy.setBaseType(original.getBaseType());
		copy.setStatus(original.getStatus());
		copy.setCurrentRevisionNumber(original.getCurrentRevisionNumber());
		copy.setThumbnailKey(original.getThumbnailKey());
		copy.setCreatedAt(now);
		copy.setUpdatedAt(now);
		mobRepository.save(copy);

		for (MobRevisionEntity revision : revisionRepository.findByMobIdOrderByRevisionNumberAsc(original.getId())) {
			revisionRepository.save(new MobRevisionEntity(
					UUID.randomUUID(),
					newMobId,
					revision.getRevisionNumber(),
					revision.getModelJson(),
					revision.getCreatedBy(),
					now));
		}

		draftRepository.findById(original.getId()).ifPresent(draft -> draftRepository.save(
				new MobDraftEntity(newMobId, draft.getModelJson(), draft.getDraftVersion(), now)));
	}

	private ProjectEntity requireProject(UUID projectId) {
		return projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
	}

	private String requireValidName(String name) {
		if (name == null || name.isBlank()) {
			throw new InvalidProjectNameException();
		}
		return name.strip();
	}

	/** Ticket 073 -- opcional: `null`/blank se guarda como `null` (nunca una cadena vacía), sin validación de contenido (a diferencia del nombre). */
	private String normalizeDescription(String description) {
		if (description == null || description.isBlank()) {
			return null;
		}
		return description.strip();
	}

	private ProjectDetail toDetail(ProjectEntity project, int mobCount) {
		return new ProjectDetail(
				project.getId().toString(), project.getName(), project.getDescription(), mobCount, project.getCreatedAt(), project.getUpdatedAt());
	}

	private ProjectSummary toSummary(ProjectEntity project) {
		List<MobEntity> mobs = mobRepository.findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(project.getId());
		List<MobThumbnail> thumbnails =
				mobs.stream().limit(3).map(m -> new MobThumbnail(m.getId().toString(), m.getThumbnailKey())).toList();
		return new ProjectSummary(
				project.getId().toString(),
				project.getName(),
				project.getDescription(),
				mobs.size(),
				thumbnails,
				deriveStatus(mobs),
				project.getCreatedAt(),
				project.getUpdatedAt());
	}

	/**
	 * Ticket 072 -- "active" si ALGÚN mob del proyecto ya salió de draft
	 * (`in_progress`/`ready`); "draft" si todos siguen en draft o el
	 * proyecto no tiene ningún mob todavía. Reutiliza la lista de mobs ya
	 * cargada para las miniaturas -- sin query adicional.
	 */
	private String deriveStatus(List<MobEntity> mobs) {
		boolean hasNonDraftMob = mobs.stream().anyMatch(m -> !"draft".equals(m.getStatus()));
		return hasNonDraftMob ? "active" : "draft";
	}

}
