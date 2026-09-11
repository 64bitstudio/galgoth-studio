package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.texture.TextureService;
import com.galgothstudio.backend.project.texture.TextureUploadResponse;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket 045 (`docs/definiciones/galgoth-studio-fase3-textura.md`,
 * Diseño técnico §6) -- ver `docs/API.md`. Mismo estilo mob-scoped y de
 * body crudo (sin multipart) que {@code MobThumbnailController}
 * (ticket 023): el cliente sube bytes crudos de un PNG, el backend
 * decodifica/valida/hashea/persiste y devuelve el `storageKey` que ÉL
 * calculó -- nunca uno propuesto por el cliente (de hecho el contrato
 * no tiene ningún campo para que el cliente proponga uno).
 */
@RestController
@RequestMapping("/api/mobs/{mobId}/texture")
public class MobTextureController {

	private final TextureService textureService;

	public MobTextureController(TextureService textureService) {
		this.textureService = textureService;
	}

	@PutMapping(consumes = MediaType.IMAGE_PNG_VALUE)
	public TextureUploadResponse upload(@PathVariable UUID mobId, @RequestBody byte[] pngBytes) {
		return textureService.upload(mobId, pngBytes);
	}

	/** Ticket 066 -- ver Javadoc de {@link TextureService#download}. Mismo patrón de respuesta que {@code MobThumbnailController#download}. */
	@GetMapping
	public ResponseEntity<byte[]> download(@PathVariable UUID mobId) {
		return textureService
				.download(mobId)
				.map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
	}

}
