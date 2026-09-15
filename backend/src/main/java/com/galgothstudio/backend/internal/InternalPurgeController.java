package com.galgothstudio.backend.internal;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket 091 -- ver `docs/API.md`. Único endpoint bajo `/api/internal/**`
 * hasta ahora; protegido por {@link InternalSecretAuthenticator}, no por
 * JWT de usuario (ver su Javadoc). `SecurityConfig` sigue en
 * {@code permitAll()} a nivel de Spring Security -- este chequeo vive
 * dentro del controlador, igual que el enforcement dueño/público/privado
 * de proyectos vivirá en `ProjectAccessGuard` (ticket 085).
 */
@RestController
@RequestMapping("/api/internal/users")
public class InternalPurgeController {

	private final PurgeAccountDataService purgeAccountDataService;
	private final InternalSecretAuthenticator internalSecretAuthenticator;

	public InternalPurgeController(
			PurgeAccountDataService purgeAccountDataService, InternalSecretAuthenticator internalSecretAuthenticator) {
		this.purgeAccountDataService = purgeAccountDataService;
		this.internalSecretAuthenticator = internalSecretAuthenticator;
	}

	@PostMapping("/{userId}/purge-projects")
	public ResponseEntity<Void> purgeProjects(
			@PathVariable UUID userId, @RequestHeader(value = "X-Internal-Secret", required = false) String providedSecret) {
		internalSecretAuthenticator.require(providedSecret);
		purgeAccountDataService.purge(userId);
		return ResponseEntity.noContent().build();
	}

}
