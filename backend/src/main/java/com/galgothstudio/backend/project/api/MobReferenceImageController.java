package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.reference.ReferenceImageService;
import com.galgothstudio.backend.project.reference.ReferenceImageSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket 024 (HU-10) -- ver `docs/API.md`. Mismo estilo mob-scoped y de
 * body crudo (sin multipart) que `MobThumbnailController` (023): el
 * content-type real de la imagen viaja en el header HTTP `Content-Type`,
 * nunca en el body ni en un campo separado.
 *
 * Deliberadamente SIN restringir `consumes` a `image/png`/`image/jpeg`:
 * dejar que Spring rechace un content-type no soportado produciría un
 * 415 sin cuerpo JSON, un formato de error distinto al resto de la API.
 * En cambio, `ReferenceImageService` valida el content-type y responde
 * el mismo `ApiErrorResponse` que cualquier otro rechazo de negocio (AC:
 * "se rechaza con un mensaje claro").
 */
@RestController
@RequestMapping("/api/mobs/{mobId}/references")
public class MobReferenceImageController {

	private final ReferenceImageService referenceImageService;

	public MobReferenceImageController(ReferenceImageService referenceImageService) {
		this.referenceImageService = referenceImageService;
	}

	@PostMapping
	public ResponseEntity<ReferenceImageSummary> upload(
			@PathVariable UUID mobId, @RequestHeader("Content-Type") String contentType, @RequestBody byte[] content) {
		ReferenceImageSummary summary = referenceImageService.upload(mobId, contentType, content);
		return ResponseEntity.status(HttpStatus.CREATED).body(summary);
	}

	@GetMapping
	public List<ReferenceImageSummary> list(@PathVariable UUID mobId) {
		return referenceImageService.list(mobId);
	}

	@GetMapping("/{referenceId}")
	public ResponseEntity<byte[]> download(@PathVariable UUID mobId, @PathVariable UUID referenceId) {
		return referenceImageService
				.download(mobId, referenceId)
				.map(image -> ResponseEntity.ok().contentType(MediaType.parseMediaType(image.getContentType())).body(image.getContent()))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
	}

}
