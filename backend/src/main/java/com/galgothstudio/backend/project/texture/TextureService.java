package com.galgothstudio.backend.project.texture;

import com.galgothstudio.backend.asset.AssetStorageService;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import com.galgothstudio.backend.project.persistence.MobRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistencia content-addressed del bitmap de textura (ticket 045,
 * `docs/definiciones/galgoth-studio-fase3-textura.md`, Diseño técnico
 * §4/§6) -- el backend es la ÚNICA autoridad del hash/`storageKey`,
 * nunca confía en uno propuesto por el cliente (de hecho el contrato
 * de este endpoint ni siquiera acepta uno: solo bytes crudos, mismo
 * estilo que {@code ThumbnailService}/{@code ReferenceImageService}).
 *
 * <p>El hash se calcula sobre los bytes CANÓNICOS re-codificados por
 * {@link ImageIO} tras decodificar (no sobre los bytes crudos subidos
 * tal cual) -- decisión explícita: dos clientes/encoders distintos
 * pueden producir bytes PNG diferentes para el mismo contenido de
 * píxeles (compresión, chunks auxiliares), lo que rompería el dedup
 * content-addressed que HU-31 exige ("dos revisiones consecutivas sin
 * cambio de bitmap comparten la misma clave automáticamente"). Re-
 * codificar antes de hashear/almacenar garantiza que el mismo contenido
 * visual siempre produce el mismo `storageKey`, sin importar qué
 * encoder lo produjo. El propio {@link ImageIO} PNG writer es
 * determinista (no embebe timestamps ni metadata variable por defecto).
 */
@Service
public class TextureService {

	private static final String CONTENT_TYPE_PNG = "image/png";
	private static final String PNG_FORMAT_NAME = "png";
	private static final String STORAGE_PREFIX = "textures/";
	private static final String STORAGE_SUFFIX = ".png";

	private final MobRepository mobRepository;
	private final AssetStorageService assetStorageService;

	public TextureService(MobRepository mobRepository, AssetStorageService assetStorageService) {
		this.mobRepository = mobRepository;
		this.assetStorageService = assetStorageService;
	}

	@Transactional(readOnly = true)
	public TextureUploadResponse upload(UUID mobId, byte[] rawBytes) {
		if (mobRepository.findById(mobId).isEmpty()) {
			throw new MobNotFoundException(mobId);
		}

		byte[] canonicalPngBytes = decodeAndReencode(rawBytes);
		String storageKey = STORAGE_PREFIX + sha256Hex(canonicalPngBytes) + STORAGE_SUFFIX;

		if (!assetStorageService.exists(storageKey)) {
			assetStorageService.put(storageKey, canonicalPngBytes, CONTENT_TYPE_PNG);
		}

		return new TextureUploadResponse(storageKey);
	}

	/** @return los bytes PNG canónicos -- nunca confía en el `Content-Type` declarado por el cliente, decodifica de verdad. */
	private byte[] decodeAndReencode(byte[] rawBytes) {
		BufferedImage image;
		try {
			image = ImageIO.read(new ByteArrayInputStream(rawBytes));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		if (image == null) {
			throw new InvalidTextureException("El archivo no se pudo decodificar como un PNG válido.");
		}

		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			if (!ImageIO.write(image, PNG_FORMAT_NAME, out)) {
				throw new InvalidTextureException("No se encontró un writer PNG para re-codificar la imagen decodificada.");
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	private String sha256Hex(byte[] content) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(content));
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 es un algoritmo obligatorio en toda implementación de la
			// plataforma Java -- si esto falla es un bug real del entorno, no
			// una condición esperada de negocio (mismo criterio que
			// UncheckedIOException en DraftPersistenceService).
			throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
		}
	}

}
