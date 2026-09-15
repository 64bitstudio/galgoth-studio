package com.galgothstudio.backend.project.mob;

import com.galgothstudio.backend.project.access.ProjectAccessGuard;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import com.galgothstudio.backend.project.persistence.MobEntity;
import com.galgothstudio.backend.project.persistence.MobRepository;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
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

	private final MobRepository mobRepository;
	private final ProjectAccessGuard projectAccessGuard;

	public MobService(MobRepository mobRepository, ProjectAccessGuard projectAccessGuard) {
		this.mobRepository = mobRepository;
		this.projectAccessGuard = projectAccessGuard;
	}

	/** Ticket 085 -- exige dueño real del proyecto (agregar un mob es una mutación). */
	@Transactional
	public MobSummary create(UUID projectId, String callerId, String name, String baseType) {
		projectAccessGuard.requireOwner(projectId, callerId);
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

	/** Ticket 085 -- lectura: dueño real o proyecto {@code PUBLIC} (`callerId` nullable, caller anónimo). */
	@Transactional(readOnly = true)
	public List<MobSummary> list(UUID projectId, String callerId) {
		projectAccessGuard.requireViewable(projectId, callerId);
		return mobRepository.findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(projectId).stream().map(this::toSummary).toList();
	}

	/**
	 * Ticket 034 -- ruta prevista desde el bootstrap del proyecto (`docs/API.md`, "Rutas previstas"), sin `projectId` en el path a propósito (mismo criterio que `MobDraftController`/`MobThumbnailController`: el mob ya se identifica solo por su id).
	 *
	 * <p>Ticket 085 -- el mob debe existir primero (`MOB_NOT_FOUND` si no) antes de resolver su proyecto y delegar la visibilidad al guard.
	 */
	@Transactional(readOnly = true)
	public MobSummary get(UUID mobId, String callerId) {
		MobEntity mob = requireMob(mobId);
		projectAccessGuard.requireViewable(mob.getProjectId(), callerId);
		return toSummary(mob);
	}

	/**
	 * Ticket 071 -- "Continuar trabajando" (Inicio): los `limit` mobs
	 * editados más recientemente, cruzando todos los proyectos no
	 * eliminados. `limit` ya validado por el controller (positivo, con un
	 * tope razonable) antes de llegar acá.
	 *
	 * <p>Ticket 084 -- {@code ownerId} filtra a los proyectos del dueño
	 * autenticado (ya validado por el controller, nunca null).
	 */
	@Transactional(readOnly = true)
	public List<RecentMobSummary> listRecentAcrossProjects(int limit, String ownerId) {
		return mobRepository.findRecentAcrossProjects(ownerId, PageRequest.of(0, limit)).stream().map(this::toRecentSummary).toList();
	}

	/** Ticket 039 -- mismo criterio de validación que `create`. Ticket 085 -- mutación, exige dueño real del proyecto del mob. */
	@Transactional
	public MobSummary rename(UUID mobId, String callerId, String newName) {
		MobEntity mob = requireMob(mobId);
		projectAccessGuard.requireOwner(mob.getProjectId(), callerId);
		mob.setName(requireValidName(newName));
		mob.setUpdatedAt(Instant.now());
		mobRepository.save(mob);
		return toSummary(mob);
	}

	/** Ticket 039 -- soft-delete, mismo criterio que `ProjectService.softDelete`. Ticket 085 -- mutación, exige dueño real. */
	@Transactional
	public void softDelete(UUID mobId, String callerId) {
		MobEntity mob = requireMob(mobId);
		projectAccessGuard.requireOwner(mob.getProjectId(), callerId);
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

	private RecentMobSummary toRecentSummary(MobEntity mob) {
		return new RecentMobSummary(
				mob.getId().toString(),
				mob.getProjectId().toString(),
				mob.getName(),
				mob.getBaseType(),
				mob.getStatus(),
				mob.getThumbnailKey(),
				mob.getUpdatedAt());
	}

}
