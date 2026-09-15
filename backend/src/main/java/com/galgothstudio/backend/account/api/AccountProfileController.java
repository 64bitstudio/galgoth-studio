package com.galgothstudio.backend.account.api;

import com.galgothstudio.backend.account.PreferencesRequest;
import com.galgothstudio.backend.account.UserProfileResponse;
import com.galgothstudio.backend.account.UserProfileService;
import com.galgothstudio.backend.project.UnauthenticatedRequestException;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket 091 -- ver `docs/API.md`. Mismo mecanismo de auth que
 * {@code ProjectController} (ticket 084): el {@code sub} del JWT identifica
 * al caller, nunca un id en la URL -- cada usuario solo puede leer/editar
 * su propio perfil de producto.
 */
@RestController
@RequestMapping("/api/account")
public class AccountProfileController {

	private final UserProfileService userProfileService;

	public AccountProfileController(UserProfileService userProfileService) {
		this.userProfileService = userProfileService;
	}

	@GetMapping("/profile")
	public UserProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
		return userProfileService.getProfile(requireUserId(jwt));
	}

	@PatchMapping("/preferences")
	public UserProfileResponse updatePreferences(@AuthenticationPrincipal Jwt jwt, @RequestBody PreferencesRequest request) {
		return userProfileService.updatePreferences(requireUserId(jwt), request);
	}

	private UUID requireUserId(Jwt jwt) {
		if (jwt == null) {
			throw new UnauthenticatedRequestException();
		}
		return UUID.fromString(jwt.getSubject());
	}

}
