package com.galgothstudio.backend.account.api;

import com.galgothstudio.backend.account.AvatarResponse;
import com.galgothstudio.backend.account.UserProfileService;
import com.galgothstudio.backend.project.UnauthenticatedRequestException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket 091 -- ver `docs/API.md`. Mismo estilo de body crudo (sin
 * multipart) que {@code MobReferenceImageController} (024): el
 * content-type real viaja en el header HTTP `Content-Type`.
 *
 * <p>La subida ({@code POST}) exige sesión -- solo el dueño sube SU propio
 * avatar, nunca el de otro id. La descarga ({@code GET .../{userId}}) es
 * pública a propósito: el avatar de un usuario debe poder mostrarse a
 * cualquiera (ej. como autor de un proyecto público en Explorar, ticket
 * 092), igual que la imagen de un proyecto público es visible sin sesión.
 */
@RestController
@RequestMapping("/api/account/avatar")
public class AccountAvatarController {

	private final UserProfileService userProfileService;

	public AccountAvatarController(UserProfileService userProfileService) {
		this.userProfileService = userProfileService;
	}

	@PostMapping
	public AvatarResponse upload(
			@AuthenticationPrincipal Jwt jwt, @RequestHeader("Content-Type") String contentType, @RequestBody byte[] content) {
		return userProfileService.uploadAvatar(requireUserId(jwt), contentType, content);
	}

	@GetMapping("/{userId}")
	public ResponseEntity<byte[]> download(@PathVariable UUID userId) {
		return userProfileService
				.downloadAvatar(userId)
				.map(avatar -> ResponseEntity.ok().contentType(MediaType.parseMediaType(avatar.contentType())).body(avatar.content()))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
	}

	private UUID requireUserId(Jwt jwt) {
		if (jwt == null) {
			throw new UnauthenticatedRequestException();
		}
		return UUID.fromString(jwt.getSubject());
	}

}
