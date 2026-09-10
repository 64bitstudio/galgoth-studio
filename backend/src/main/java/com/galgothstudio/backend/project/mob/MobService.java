package com.galgothstudio.backend.project.mob;

import com.galgothstudio.backend.project.ProjectNotFoundException;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import com.galgothstudio.backend.project.persistence.MobEntity;
import com.galgothstudio.backend.project.persistence.MobRepository;
import com.galgothstudio.backend.project.persistence.ProjectRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de mobs dentro de un proyecto (ticket 022, HU-03/HU-04 -- crear/
 * listar; ticket 039 agrega rename/soft-delete individual, mismo
 * criterio que `ProjectService` -- ver `V2__mobs_soft_delete.sql` para
 * el porqué de soft-delete en vez de hard-delete). Duplicate a nivel de
 * mob individual sigue sin pedirse -- no se inventa.
 */
@Service
public class MobService {

	/** Mismos 5 valores del CHECK constraint `mobs.base_type` (`V1__init_schema.sql`, ticket 003). */
	private static final Set<String> VALID_BASE_TYPES = Set.of("humanoid", "arachnid", "quadruped", "flying", "custom");

	private final ProjectRepository projectRepository;
	private final MobRepository mobRepository;

	public MobService(ProjectRepository projectRepository, MobRepository mobRepository) {
		this.projectRepository = projectRepository;
		this.mobRepository = mobRepository;
	}

	@Transactional
	public MobSummary create(UUID projectId, String name, String baseType) {
		if (projectRepository.findByIdAndDeletedAtIsNull(projectId).isEmpty()) {
			throw new ProjectNotFoundException(projectId);
		}
		String validName = requireValidName(name);
		String validBaseType = requireValidBaseType(baseType);

		Instant now = Instant.now();
		MobEntity mob = new MobEntity(UUID.randomUUID());
		mob.setProjectId(projectId);
		mob.setName(validName);
		mob.setBaseType(validBaseType);
		// AC #2: un mob nuevo siempre arranca en "draft", current_revision_number=0
		// (default de la columna) y SIN fila en mob_drafts -- no existe hasta el
		// primer commit (autosave/Guardar, ticket 020), nunca se crea aquí.
		mob.setStatus("draft");
		mob.setCreatedAt(now);
		mob.setUpdatedAt(now);
		mobRepository.save(mob);

		return toSummary(mob);
	}

	@Transactional(readOnly = true)
	public List<MobSummary> list(UUID projectId) {
		if (projectRepository.findByIdAndDeletedAtIsNull(projectId).isEmpty()) {
			throw new ProjectNotFoundException(projectId);
		}
		return mobRepository.findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId).stream().map(this::toSummary).toList();
	}

	/** Ticket 034 -- ruta prevista desde el bootstrap del proyecto (`docs/API.md`, "Rutas previstas"), sin `projectId` en el path a propósito (mismo criterio que `MobDraftController`/`MobThumbnailController`: el mob ya se identifica solo por su id). */
	@Transactional(readOnly = true)
	public MobSummary get(UUID mobId) {
		MobEntity mob = requireMob(mobId);
		return toSummary(mob);
	}

	/** Ticket 039 -- mismo criterio de validación que `create`. */
	@Transactional
	public MobSummary rename(UUID mobId, String newName) {
		MobEntity mob = requireMob(mobId);
		mob.setName(requireValidName(newName));
		mob.setUpdatedAt(Instant.now());
		mobRepository.save(mob);
		return toSummary(mob);
	}

	/** Ticket 039 -- soft-delete, mismo criterio que `ProjectService.softDelete`. */
	@Transactional
	public void softDelete(UUID mobId) {
		MobEntity mob = requireMob(mobId);
		mob.setDeletedAt(Instant.now());
		mobRepository.save(mob);
	}

	private MobEntity requireMob(UUID mobId) {
		return mobRepository.findByIdAndDeletedAtIsNull(mobId).orElseThrow(() -> new MobNotFoundException(mobId));
	}

	private String requireValidName(String name) {
		if (name == null || name.isBlank()) {
			throw new InvalidMobRequestException("El nombre del mob no puede estar vacío.");
		}
		return name.strip();
	}

	private String requireValidBaseType(String baseType) {
		if (baseType == null || !VALID_BASE_TYPES.contains(baseType)) {
			throw new InvalidMobRequestException(
					"baseType inválido: '" + baseType + "' -- debe ser uno de " + VALID_BASE_TYPES);
		}
		return baseType;
	}

	private MobSummary toSummary(MobEntity mob) {
		return new MobSummary(
				mob.getId().toString(), mob.getName(), mob.getBaseType(), mob.getStatus(), mob.getThumbnailKey(), mob.getUpdatedAt());
	}

}
