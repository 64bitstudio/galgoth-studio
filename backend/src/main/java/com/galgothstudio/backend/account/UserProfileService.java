package com.galgothstudio.backend.account;

import com.galgothstudio.backend.account.persistence.UserProfileEntity;
import com.galgothstudio.backend.account.persistence.UserProfileRepository;
import com.galgothstudio.backend.asset.AssetStorageService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Perfil de producto" de cada usuario (ticket 091,
 * docs/definiciones/perfil-de-usuario.md) -- avatar (MinIO, mismo cliente
 * genérico que thumbnails/referencias, ticket 023) y preferencias de
 * notificación. Persistir las preferencias todavía NO cambia ningún envío
 * real de correo (decisión explícita del documento de definición, ticket
 * de seguimiento aparte) -- este servicio solo guarda/lee el valor.
 *
 * A diferencia del thumbnail (key fija, siempre PNG) pero igual que las
 * imágenes de referencia, el content-type del avatar varía por archivo --
 * de ahí que la key completa Y el content-type se guarden en la fila
 * (`user_profile.avatar_key`/`avatar_content_type`) en vez de
 * reconstruirse: una resubida en un formato distinto no deja ninguna
 * referencia rota, solo bytes huérfanos en MinIO bajo la key anterior
 * (mismo costo aceptado que ya documenta `ThumbnailService` para su propio
 * caso de sobrescritura).
 */
@Service
public class UserProfileService {

	static final int MAX_AVATAR_SIZE_BYTES = 5 * 1024 * 1024;
	private static final Map<String, String> SUPPORTED_AVATAR_CONTENT_TYPES = Map.of("image/png", "png", "image/jpeg", "jpg");

	private final UserProfileRepository userProfileRepository;
	private final AssetStorageService assetStorageService;

	public UserProfileService(UserProfileRepository userProfileRepository, AssetStorageService assetStorageService) {
		this.userProfileRepository = userProfileRepository;
		this.assetStorageService = assetStorageService;
	}

	@Transactional
	public AvatarResponse uploadAvatar(UUID userId, String rawContentType, byte[] content) {
		String contentType = normalizedContentType(rawContentType);
		String extension = requireSupportedContentType(contentType);
		requireValidSize(content);
		requireDecodableImage(content);

		String storageKey = "users/" + userId + "/avatar-" + UUID.randomUUID() + "." + extension;
		assetStorageService.put(storageKey, content, contentType);

		UserProfileEntity profile = findOrCreate(userId);
		profile.setAvatarKey(storageKey);
		profile.setAvatarContentType(contentType);
		profile.setUpdatedAt(Instant.now());
		userProfileRepository.save(profile);

		return new AvatarResponse(avatarUrlFor(userId));
	}

	@Transactional(readOnly = true)
	public Optional<StoredAvatar> downloadAvatar(UUID userId) {
		return userProfileRepository
				.findById(userId)
				.filter(profile -> profile.getAvatarKey() != null)
				.flatMap(profile -> assetStorageService
						.get(profile.getAvatarKey())
						.map(bytes -> new StoredAvatar(bytes, profile.getAvatarContentType())));
	}

	@Transactional(readOnly = true)
	public UserProfileResponse getProfile(UUID userId) {
		return userProfileRepository.findById(userId).map(this::toResponse).orElseGet(UserProfileService::defaultResponse);
	}

	@Transactional
	public UserProfileResponse updatePreferences(UUID userId, PreferencesRequest request) {
		UserProfileEntity profile = findOrCreate(userId);
		profile.setNotifyEmail(request.notifyEmail());
		profile.setNotifyProductNews(request.notifyProductNews());
		profile.setNotifySaveReminders(request.notifySaveReminders());
		profile.setUpdatedAt(Instant.now());
		userProfileRepository.save(profile);
		return toResponse(profile);
	}

	/** Ticket 091 -- llamado por {@code PurgeAccountDataService} al eliminar una cuenta; no falla si el usuario nunca tuvo perfil de producto. */
	@Transactional
	public void deleteProfile(UUID userId) {
		userProfileRepository.findById(userId).ifPresent(userProfileRepository::delete);
	}

	private UserProfileEntity findOrCreate(UUID userId) {
		return userProfileRepository.findById(userId).orElseGet(() -> new UserProfileEntity(userId));
	}

	private static String avatarUrlFor(UUID userId) {
		return "/api/account/avatar/" + userId;
	}

	private UserProfileResponse toResponse(UserProfileEntity profile) {
		String avatarUrl = profile.getAvatarKey() == null ? null : avatarUrlFor(profile.getUserId());
		return new UserProfileResponse(
				avatarUrl, profile.isNotifyEmail(), profile.isNotifyProductNews(), profile.isNotifySaveReminders());
	}

	private static UserProfileResponse defaultResponse() {
		return new UserProfileResponse(null, true, true, true);
	}

	/** Mismo hallazgo real que {@code ReferenceImageService} (ticket 024): distintos clientes HTTP agregan parámetros (`;charset=...`) incluso a un content-type binario. */
	private String normalizedContentType(String rawContentType) {
		try {
			MediaType parsed = MediaType.parseMediaType(rawContentType);
			return parsed.getType() + "/" + parsed.getSubtype();
		} catch (InvalidMediaTypeException _) {
			throw new InvalidAvatarException("Content-Type inválido: '" + rawContentType + "'.");
		}
	}

	private String requireSupportedContentType(String contentType) {
		String extension = SUPPORTED_AVATAR_CONTENT_TYPES.get(contentType);
		if (extension == null) {
			throw new InvalidAvatarException(
					"Formato de imagen no soportado: '" + contentType + "' -- solo se aceptan " + SUPPORTED_AVATAR_CONTENT_TYPES.keySet());
		}
		return extension;
	}

	private void requireValidSize(byte[] content) {
		if (content.length > MAX_AVATAR_SIZE_BYTES) {
			throw new InvalidAvatarException(
					"La imagen pesa " + content.length + " bytes -- el máximo soportado es " + MAX_AVATAR_SIZE_BYTES + " bytes (5MB).");
		}
	}

	private void requireDecodableImage(byte[] content) {
		BufferedImage image;
		try {
			image = ImageIO.read(new ByteArrayInputStream(content));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (image == null) {
			throw new InvalidAvatarException("El archivo no se pudo decodificar como una imagen válida.");
		}
	}

}
