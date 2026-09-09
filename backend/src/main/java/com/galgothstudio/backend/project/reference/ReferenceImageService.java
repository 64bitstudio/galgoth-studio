package com.galgothstudio.backend.project.reference;

import com.galgothstudio.backend.asset.AssetStorageService;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import com.galgothstudio.backend.project.persistence.MobRepository;
import com.galgothstudio.backend.project.persistence.ReferenceImageEntity;
import com.galgothstudio.backend.project.persistence.ReferenceImageRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subida de la imagen de concept art de referencia (ticket 024, HU-10) --
 * primer paso del wizard de generación IA (026+). A diferencia del
 * thumbnail (ticket 023, siempre PNG y siempre 1 por mob, key fija
 * sobrescrita), acá puede haber VARIAS imágenes por mob (`reference_images`
 * es append-only, un `INSERT` por subida, nunca un `UPDATE`) y el
 * content-type varía por archivo -- de ahí una key única por imagen
 * (`mobs/{mobId}/references/{id}.<ext>`) en vez de una key fija.
 *
 * Límites concretos (formatos/tamaño), dejados abiertos a propósito por
 * el documento de definición para resolverse en este ticket, VoBo
 * explícito del Product Owner: solo PNG/JPEG, máximo 10MB -- cubre
 * holgadamente la muestra real del build pack
 * (`carcomido_reference.png`, ~3MB) sin abrir la puerta a archivos
 * arbitrariamente grandes.
 */
@Service
public class ReferenceImageService {

	static final int MAX_SIZE_BYTES = 10 * 1024 * 1024;
	private static final Map<String, String> SUPPORTED_CONTENT_TYPES = Map.of("image/png", "png", "image/jpeg", "jpg");

	private final MobRepository mobRepository;
	private final ReferenceImageRepository referenceImageRepository;
	private final AssetStorageService assetStorageService;

	public ReferenceImageService(
			MobRepository mobRepository, ReferenceImageRepository referenceImageRepository, AssetStorageService assetStorageService) {
		this.mobRepository = mobRepository;
		this.referenceImageRepository = referenceImageRepository;
		this.assetStorageService = assetStorageService;
	}

	@Transactional
	public ReferenceImageSummary upload(UUID mobId, String rawContentType, byte[] content) {
		requireMobExists(mobId);
		String contentType = normalizedContentType(rawContentType);
		String extension = requireSupportedContentType(contentType);
		requireValidSize(content);
		int[] dimensions = requireDecodableImage(content);

		UUID id = UUID.randomUUID();
		String storageKey = "mobs/" + mobId + "/references/" + id + "." + extension;
		assetStorageService.put(storageKey, content, contentType);

		Instant now = Instant.now();
		ReferenceImageEntity entity =
				new ReferenceImageEntity(id, mobId, storageKey, dimensions[0], dimensions[1], contentType, now);
		referenceImageRepository.save(entity);

		return toSummary(entity);
	}

	@Transactional(readOnly = true)
	public List<ReferenceImageSummary> list(UUID mobId) {
		requireMobExists(mobId);
		return referenceImageRepository.findByMobIdOrderByCreatedAtAsc(mobId).stream().map(this::toSummary).toList();
	}

	@Transactional(readOnly = true)
	public Optional<StoredReferenceImage> download(UUID mobId, UUID referenceId) {
		requireMobExists(mobId);
		Optional<ReferenceImageEntity> entity = referenceImageRepository.findByIdAndMobId(referenceId, mobId);
		if (entity.isEmpty()) {
			return Optional.empty();
		}
		return assetStorageService
				.get(entity.get().getStorageKey())
				.map(bytes -> new StoredReferenceImage(bytes, entity.get().getContentType()));
	}

	private void requireMobExists(UUID mobId) {
		if (mobRepository.findById(mobId).isEmpty()) {
			throw new MobNotFoundException(mobId);
		}
	}

	/**
	 * Devuelve solo `tipo/subtipo` (ej. `"image/png"`), descartando
	 * cualquier parámetro (`;charset=...`) -- distintos clientes HTTP
	 * agregan parámetros al header `Content-Type` incluso para tipos
	 * binarios (confirmado real: `MockHttpServletRequestBuilder` de
	 * Spring Test añade `;charset=UTF-8` por defecto a CUALQUIER
	 * content-type, no solo texto). Comparar el header crudo por
	 * igualdad de string, sin normalizar, rechazaría imágenes válidas.
	 */
	private String normalizedContentType(String rawContentType) {
		try {
			MediaType parsed = MediaType.parseMediaType(rawContentType);
			return parsed.getType() + "/" + parsed.getSubtype();
		} catch (InvalidMediaTypeException _) {
			throw new InvalidReferenceImageException("Content-Type inválido: '" + rawContentType + "'.");
		}
	}

	private String requireSupportedContentType(String contentType) {
		String extension = SUPPORTED_CONTENT_TYPES.get(contentType);
		if (extension == null) {
			throw new InvalidReferenceImageException(
					"Formato de imagen no soportado: '" + contentType + "' -- solo se aceptan " + SUPPORTED_CONTENT_TYPES.keySet());
		}
		return extension;
	}

	private void requireValidSize(byte[] content) {
		if (content.length > MAX_SIZE_BYTES) {
			throw new InvalidReferenceImageException(
					"La imagen pesa " + content.length + " bytes -- el máximo soportado es " + MAX_SIZE_BYTES + " bytes (10MB).");
		}
	}

	/** @return {width, height} -- nunca confía en un valor provisto por el cliente, siempre decodificado de los bytes reales (mismo criterio de autoridad server-side que `baseType` en `MobService`). */
	private int[] requireDecodableImage(byte[] content) {
		BufferedImage image;
		try {
			image = ImageIO.read(new ByteArrayInputStream(content));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (image == null) {
			throw new InvalidReferenceImageException("El archivo no se pudo decodificar como una imagen válida.");
		}
		return new int[] {image.getWidth(), image.getHeight()};
	}

	private ReferenceImageSummary toSummary(ReferenceImageEntity entity) {
		String url = "/api/mobs/" + entity.getMobId() + "/references/" + entity.getId();
		return new ReferenceImageSummary(
				entity.getId().toString(), url, entity.getWidth(), entity.getHeight(), entity.getContentType(), entity.getCreatedAt());
	}

}
