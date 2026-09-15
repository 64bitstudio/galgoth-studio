package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.ChangeVisibilityRequest;
import com.galgothstudio.backend.project.CreateProjectRequest;
import com.galgothstudio.backend.project.ProjectDetail;
import com.galgothstudio.backend.project.ProjectService;
import com.galgothstudio.backend.project.ProjectSummary;
import com.galgothstudio.backend.project.RenameProjectRequest;
import com.galgothstudio.backend.project.UnauthenticatedRequestException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ticket 021 (HU-01/HU-02) -- ver `docs/API.md` para el contrato completo. */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

	private final ProjectService projectService;

	public ProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	/**
	 * Ticket 084 -- exige dueño real: sin `sub` en el JWT no hay a quién
	 * ligar el proyecto. `SecurityConfig` sigue en `permitAll()` para esta
	 * ruta hasta el ticket 085 (reglas explícitas por ruta) -- este chequeo
	 * es la pieza mínima que HU-01 necesita ya.
	 */
	@PostMapping
	public ResponseEntity<ProjectDetail> create(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateProjectRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(projectService.create(request.name(), requireOwnerId(jwt), request.ownerDisplayName()));
	}

	/** Ticket 084 -- "Mis proyectos" (HU-02): solo los del dueño autenticado. */
	@GetMapping
	public List<ProjectSummary> list(@AuthenticationPrincipal Jwt jwt) {
		return projectService.list(requireOwnerId(jwt));
	}

	private String requireOwnerId(Jwt jwt) {
		if (jwt == null) {
			throw new UnauthenticatedRequestException();
		}
		return jwt.getSubject();
	}

	/** Ticket 085 -- lectura: dueño real o proyecto {@code PUBLIC} (`jwt` nullable, caller anónimo permitido). */
	@GetMapping("/{projectId}")
	public ProjectDetail get(@PathVariable UUID projectId, @AuthenticationPrincipal Jwt jwt) {
		return projectService.get(projectId, callerId(jwt));
	}

	@PatchMapping("/{projectId}")
	public ProjectDetail rename(
			@PathVariable UUID projectId, @AuthenticationPrincipal Jwt jwt, @RequestBody RenameProjectRequest request) {
		return projectService.rename(projectId, callerId(jwt), request.name(), request.description());
	}

	@DeleteMapping("/{projectId}")
	public ResponseEntity<Void> delete(@PathVariable UUID projectId, @AuthenticationPrincipal Jwt jwt) {
		projectService.softDelete(projectId, callerId(jwt));
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{projectId}/duplicate")
	public ResponseEntity<ProjectDetail> duplicate(@PathVariable UUID projectId, @AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.status(HttpStatus.CREATED).body(projectService.duplicate(projectId, callerId(jwt)));
	}

	/** Ticket 086 (HU-4) -- mutación, exige dueño real. */
	@PatchMapping("/{projectId}/visibility")
	public ProjectDetail changeVisibility(
			@PathVariable UUID projectId, @AuthenticationPrincipal Jwt jwt, @RequestBody ChangeVisibilityRequest request) {
		return projectService.changeVisibility(projectId, callerId(jwt), request.visibility());
	}

	/** Ticket 085 -- {@code null} = caller anónimo, válido solo para lecturas de un proyecto {@code PUBLIC}. */
	private static String callerId(Jwt jwt) {
		return jwt == null ? null : jwt.getSubject();
	}

}
