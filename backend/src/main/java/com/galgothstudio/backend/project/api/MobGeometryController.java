package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.geometry.MobGeometryApplyRequest;
import com.galgothstudio.backend.project.geometry.MobGeometryApplyResponse;
import com.galgothstudio.backend.project.geometry.MobGeometryApplyService;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ticket 043 -- ver `docs/API.md` para el contrato completo del endpoint. Ticket 085 agrega enforcement dueño/público/privado vía `ProjectAccessGuard`. */
@RestController
@RequestMapping("/api/mobs/{mobId}")
public class MobGeometryController {

	private final MobGeometryApplyService geometryApplyService;

	public MobGeometryController(MobGeometryApplyService geometryApplyService) {
		this.geometryApplyService = geometryApplyService;
	}

	@PostMapping("/geometry/apply")
	public MobGeometryApplyResponse apply(
			@PathVariable UUID mobId, @AuthenticationPrincipal Jwt jwt, @RequestBody MobGeometryApplyRequest request) {
		return geometryApplyService.apply(mobId, callerId(jwt), request);
	}

	private static String callerId(Jwt jwt) {
		return jwt == null ? null : jwt.getSubject();
	}

}
