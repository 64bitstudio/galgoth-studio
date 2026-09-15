package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.ProjectService;
import com.galgothstudio.backend.project.ProjectSummary;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket 086 (HU-5) -- "Explorar": escaparate público, sin sesión. Mismo
 * shape (`ProjectSummary`) que "Mis proyectos" (021) -- una tarjeta de
 * Explorar necesita exactamente los mismos datos (miniaturas, conteo de
 * mobs, autor). Sin búsqueda/filtros/paginación en esta primera pasada
 * (decisión explícita del documento de definición).
 */
@RestController
@RequestMapping("/api/explore")
public class ExploreProjectController {

	private final ProjectService projectService;

	public ExploreProjectController(ProjectService projectService) {
		this.projectService = projectService;
	}

	@GetMapping("/projects")
	public List<ProjectSummary> projects() {
		return projectService.listPublic();
	}

}
