package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.CreateProjectRequest;
import com.galgothstudio.backend.project.ProjectDetail;
import com.galgothstudio.backend.project.ProjectService;
import com.galgothstudio.backend.project.ProjectSummary;
import com.galgothstudio.backend.project.RenameProjectRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

	@PostMapping
	public ResponseEntity<ProjectDetail> create(@RequestBody CreateProjectRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(projectService.create(request.name()));
	}

	@GetMapping
	public List<ProjectSummary> list() {
		return projectService.list();
	}

	@GetMapping("/{projectId}")
	public ProjectDetail get(@PathVariable UUID projectId) {
		return projectService.get(projectId);
	}

	@PatchMapping("/{projectId}")
	public ProjectDetail rename(@PathVariable UUID projectId, @RequestBody RenameProjectRequest request) {
		return projectService.rename(projectId, request.name());
	}

	@DeleteMapping("/{projectId}")
	public ResponseEntity<Void> delete(@PathVariable UUID projectId) {
		projectService.softDelete(projectId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{projectId}/duplicate")
	public ResponseEntity<ProjectDetail> duplicate(@PathVariable UUID projectId) {
		return ResponseEntity.status(HttpStatus.CREATED).body(projectService.duplicate(projectId));
	}

}
