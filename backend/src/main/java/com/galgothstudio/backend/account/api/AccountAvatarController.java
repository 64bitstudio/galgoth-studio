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
 * avatar, nunca el de otro id. La descarga ({@code GET .../{publicAvatarId}})
 * es pública a propósito: el avatar de un usuario debe poder mostrarse a
 * cualquiera (ej. como autor de un proyecto público en Explorar, ticket
 * 092), igual que la imagen de un proyecto público es visible sin sesión.
 *
 * <p><b>Hallazgo real de seguridad (ticket 106, auth-core-mc#071):</b>
 * hasta esta corrección, la ruta de descarga usaba el {@code userId}
 * real (el mismo {@code sub} del JWT de auth-core-mc) -- exponer ese id
 * a cualquier visitante sin sesión (vía `ProjectSummary.avatarUrl` en
 * Explorar) era exactamente el dato que hacía explotable un secuestro de
 * cuenta contra endpoints de auth-core-mc que confiaban un `userId` sin
 * autenticar (ya cerrado del lado de auth-core-mc). Se reemplazó por
 * {@code publicAvatarId}: un id de servicio aparte, sin relación
 * reconstruible con la identidad real (ver
 * {@code UserProfileEntity.publicAvatarId}).
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

	@GetMapping("/{publicAvatarId}")
	public ResponseEntity<byte[]> download(@PathVariable UUID publicAvatarId) {
		return userProfileService
				.downloadAvatar(publicAvatarId)
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
