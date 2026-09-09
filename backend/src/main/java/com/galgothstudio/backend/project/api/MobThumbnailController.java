package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.thumbnail.ThumbnailService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket 023 (Diseño técnico §8) -- ver `docs/API.md`. Mismo estilo de
 * ruta mob-scoped (no anidada bajo `/api/projects/...`) que
 * `MobDraftController` (ticket 020).
 */
@RestController
@RequestMapping("/api/mobs/{mobId}/thumbnail")
public class MobThumbnailController {

	private final ThumbnailService thumbnailService;

	public MobThumbnailController(ThumbnailService thumbnailService) {
		this.thumbnailService = thumbnailService;
	}

	@PostMapping(consumes = MediaType.IMAGE_PNG_VALUE)
	public ResponseEntity<Void> upload(@PathVariable UUID mobId, @RequestBody byte[] pngBytes) {
		thumbnailService.upload(mobId, pngBytes);
		return ResponseEntity.noContent().build();
	}

	@GetMapping
	public ResponseEntity<byte[]> download(@PathVariable UUID mobId) {
		return thumbnailService
				.download(mobId)
				.map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
	}

}
