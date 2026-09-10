package com.galgothstudio.backend.aiorchestrator.api;

import com.galgothstudio.backend.asset.AssetStorageService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sirve los assets temporales de preview de `preview_texture_patch`
 * (ticket 054, Diseño técnico §13) cuando el payload excede el umbral
 * inline de 32 KB -- el `url` que ese evento emite es exactamente
 * `/api/texture-previews/{jobId}/{fileName}`, resuelto acá.
 *
 * <p><b>Deliberadamente su PROPIO controlador, restringido a este
 * prefijo</b> -- nunca un endpoint genérico "servir cualquier storageKey
 * por URL": {@code AssetStorageService} no distingue permisos por key, así
 * que un endpoint así de amplio expondría también `textures/{hash}.png`/
 * imágenes de referencia/thumbnails sin ningún control adicional. Acotar
 * la ruta a `texture-previews/{jobId}/...` (mismo prefijo que
 * {@code TextureGenerationService#PREVIEW_ASSET_PREFIX}) es la forma más
 * simple de no ampliar la superficie expuesta más allá de lo que este
 * ticket necesita.
 */
@RestController
public class TexturePreviewAssetController {

	private final AssetStorageService assetStorageService;

	public TexturePreviewAssetController(AssetStorageService assetStorageService) {
		this.assetStorageService = assetStorageService;
	}

	@GetMapping("/api/texture-previews/{jobId}/{fileName}")
	public ResponseEntity<byte[]> get(@PathVariable UUID jobId, @PathVariable String fileName) {
		String key = "texture-previews/" + jobId + "/" + fileName;
		return assetStorageService
				.get(key)
				.map(bytes -> ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(bytes))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
	}

}
