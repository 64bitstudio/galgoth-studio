package com.galgothstudio.backend.project;

import com.galgothstudio.backend.project.access.ProjectAccessGuard;
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
	/** Ticket 084 -- cada proyecto nace privado (decisión de Marco); publicarlo es una acción explícita del dueño (ticket 086). */
	private static final String PRIVATE = "PRIVATE";

	private final ProjectRepository projectRepository;
	private final MobRepository mobRepository;
	private final MobDraftRepository draftRepository;
	private final MobRevisionRepository revisionRepository;
	private final ProjectAccessGuard projectAccessGuard;

	public ProjectService(
			ProjectRepository projectRepository,
			MobRepository mobRepository,
			MobDraftRepository draftRepository,
			MobRevisionRepository revisionRepository,
			ProjectAccessGuard projectAccessGuard) {
		this.projectRepository = projectRepository;
		this.mobRepository = mobRepository;
		this.draftRepository = draftRepository;
		this.revisionRepository = revisionRepository;
		this.projectAccessGuard = projectAccessGuard;
	}

	/**
	 * Ticket 084 -- {@code ownerId} es el {@code sub} (user id) del JWT de
	 * auth-core-mc, ya validado por el controlador antes de llegar aquí
	 * (nunca null). Ticket 086 -- {@code ownerDisplayName} opcional
	 * (`null` si el caller no lo envió).
	 */
	@Transactional
	public ProjectDetail create(String name, String ownerId, String ownerDisplayName) {
		String validName = requireValidName(name);
		Instant now = Instant.now();
		ProjectEntity project = new ProjectEntity(UUID.randomUUID(), validName, ownerId, PRIVATE, now, now);
		project.setOwnerDisplayName(blankToNull(ownerDisplayName));
		projectRepository.save(project);
		return toDetail(project, 0);
	}

	/** Ticket 084 -- "Mis proyectos" (HU-02): solo los del dueño autenticado. */
	@Transactional(readOnly = true)
	public List<ProjectSummary> list(String ownerId) {
		return projectRepository.findByOwnerRefAndDeletedAtIsNullOrderByUpdatedAtDesc(ownerId).stream().map(this::toSummary).toList();
	}

	/** Ticket 085 -- lectura: dueño real o proyecto {@code PUBLIC} (`callerId` nullable, caller anónimo). */
	@Transactional(readOnly = true)
	public ProjectDetail get(UUID projectId, String callerId) {
		ProjectEntity project = projectAccessGuard.requireViewable(projectId, callerId);
		return toDetail(project, (int) mobRepository.countByProjectIdAndDeletedAtIsNull(projectId));
	}

	/** Ticket 073 -- `description` siempre explícita (nunca ambigua entre "ausente" y "null"), ver docstring de `RenameProjectRequest`. Ticket 085 -- mutación, exige dueño real. */
	@Transactional
	public ProjectDetail rename(UUID projectId, String callerId, String newName, String description) {
		ProjectEntity project = projectAccessGuard.requireOwner(projectId, callerId);
		project.setName(requireValidName(newName));
		project.setDescription(normalizeDescription(description));
		project.setUpdatedAt(Instant.now());
		projectRepository.save(project);
		return toDetail(project, (int) mobRepository.countByProjectIdAndDeletedAtIsNull(projectId));
	}

	/**
	 * Ticket 086 (HU-4) -- mutación, exige dueño real. `"PRIVATE"`/`"PUBLIC"`
	 * son los únicos valores válidos (mismo `CHECK` de la columna, `V5`).
	 */
	@Transactional
	public ProjectDetail changeVisibility(UUID projectId, String callerId, String visibility) {
		if (!"PRIVATE".equals(visibility) && !"PUBLIC".equals(visibility)) {
			throw new InvalidVisibilityException(visibility);
		}
		ProjectEntity project = projectAccessGuard.requireOwner(projectId, callerId);
		project.setVisibility(visibility);
		project.setUpdatedAt(Instant.now());
		projectRepository.save(project);
		return toDetail(project, (int) mobRepository.countByProjectIdAndDeletedAtIsNull(projectId));
	}

	/** Ticket 086 (HU-5) -- Explorar: proyectos `PUBLIC` de cualquier dueño, `permitAll()` a nivel de Spring (ver `SecurityConfig`). */
	@Transactional(readOnly = true)
	public List<ProjectSummary> listPublic() {
		return projectRepository.findByVisibilityAndDeletedAtIsNullOrderByUpdatedAtDesc("PUBLIC").stream()
				.map(this::toSummary)
				.toList();
	}

	/** Ticket 085 -- mutación, exige dueño real. */
	@Transactional
	public void softDelete(UUID projectId, String callerId) {
		ProjectEntity project = projectAccessGuard.requireOwner(projectId, callerId);
		project.setDeletedAt(Instant.now());
		projectRepository.save(project);
	}

	/**
	 * Ticket 091 -- llamado por {@code PurgeAccountDataService} al eliminar
	 * una cuenta (ticket 064 de auth-core-mc, vía el endpoint interno). Se
	 * lleva TODOS los proyectos del dueño, públicos o privados por igual --
	 * eliminar la cuenta no debe dejar proyectos públicos huérfanos
	 * visibles en Explorar (decisión de Marco, docs/definiciones/perfil-de-usuario.md).
	 * Sin `callerId`/guard a propósito: el caller es otro backend (vía el
	 * secreto compartido de {@code InternalSecretAuthenticator}), no un
	 * usuario -- no hay contra qué comparar ownership.
	 */
	@Transactional
	public void purgeAllForOwner(String ownerId) {
		Instant now = Instant.now();
		for (ProjectEntity project : projectRepository.findByOwnerRefAndDeletedAtIsNullOrderByUpdatedAtDesc(ownerId)) {
			project.setDeletedAt(now);
			projectRepository.save(project);
		}
	}

	/** Ticket 085 -- mutación, exige dueño real. */
	@Transactional
	public ProjectDetail duplicate(UUID projectId, String callerId) {
		ProjectEntity original = projectAccessGuard.requireOwner(projectId, callerId);
		Instant now = Instant.now();
		// Ticket 084 -- la copia nace PRIVATE sin importar la visibilidad del original: duplicar no debe publicar nada por accidente.
		ProjectEntity copy =
				new ProjectEntity(UUID.randomUUID(), original.getName() + COPY_SUFFIX, original.getOwnerRef(), PRIVATE, now, now);
		copy.setDescription(original.getDescription()); // ticket 073 -- copia profunda: la descripción también se copia.
		copy.setOwnerDisplayName(original.getOwnerDisplayName()); // ticket 086 -- mismo dueño, mismo nombre a mostrar.
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

	private String requireValidName(String name) {
		if (name == null || name.isBlank()) {
			throw new InvalidProjectNameException();
		}
		return name.strip();
	}

	/** Ticket 073 -- opcional: `null`/blank se guarda como `null` (nunca una cadena vacía), sin validación de contenido (a diferencia del nombre). */
	private String normalizeDescription(String description) {
		return blankToNull(description);
	}

	/** Ticket 086 -- mismo criterio que {@link #normalizeDescription}, reutilizado para {@code ownerDisplayName}: `null`/blank nunca se guarda como cadena vacía. */
	private static String blankToNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		return value.strip();
	}

	private ProjectDetail toDetail(ProjectEntity project, int mobCount) {
		return new ProjectDetail(
				project.getId().toString(),
				project.getName(),
				project.getDescription(),
				mobCount,
				project.getVisibility(),
				project.getOwnerDisplayName(),
				project.getCreatedAt(),
				project.getUpdatedAt());
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
				project.getVisibility(),
				project.getOwnerDisplayName(),
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
