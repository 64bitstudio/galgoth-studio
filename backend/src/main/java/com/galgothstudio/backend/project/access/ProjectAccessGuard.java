package com.galgothstudio.backend.project.access;

import com.galgothstudio.backend.project.ProjectNotFoundException;
import com.galgothstudio.backend.project.persistence.ProjectEntity;
import com.galgothstudio.backend.project.persistence.ProjectRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Ticket 085 (docs/definiciones/proyectos-por-usuario-y-explorar.md,
 * Diseño técnico §3) -- un solo punto de autorización para los 9
 * controladores bajo `/api/projects/{projectId}/**` (y sus recursos
 * anidados por `mobId`, resueltos a `projectId` por el caller antes de
 * llamar acá), en vez de repetir "¿es mío, es público, o no me
 * pertenece?" en cada servicio -- mismo criterio que `AdminAccessPolicy`
 * en auth-core-mc (ticket 011 de ese repo).
 *
 * <p>{@code callerId} es el {@code sub} del JWT ({@code null} = caller
 * anónimo, válido para {@link #requireViewable}). Ninguno de los dos
 * métodos revela si un proyecto existe cuando el caller no tiene acceso
 * -- siempre {@link ProjectNotFoundException} (`404`), nunca `403`.
 */
@Component
public class ProjectAccessGuard {

	private final ProjectRepository projectRepository;

	public ProjectAccessGuard(ProjectRepository projectRepository) {
		this.projectRepository = projectRepository;
	}

	/** Para toda mutación (crear anidado, renombrar, borrar, subir un asset, etc.) -- exige dueño real. */
	public ProjectEntity requireOwner(UUID projectId, String callerId) {
		ProjectEntity project = requireProject(projectId);
		if (callerId == null || !callerId.equals(project.getOwnerRef())) {
			throw new ProjectNotFoundException(projectId);
		}
		return project;
	}

	/** Para lectura -- dueño real, o proyecto {@code PUBLIC} (cualquier caller, incluido anónimo). */
	public ProjectEntity requireViewable(UUID projectId, String callerId) {
		ProjectEntity project = requireProject(projectId);
		boolean isOwner = callerId != null && callerId.equals(project.getOwnerRef());
		boolean isPublic = "PUBLIC".equals(project.getVisibility());
		if (!isOwner && !isPublic) {
			throw new ProjectNotFoundException(projectId);
		}
		return project;
	}

	private ProjectEntity requireProject(UUID projectId) {
		return projectRepository.findByIdAndDeletedAtIsNull(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));
	}

}
