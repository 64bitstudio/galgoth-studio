package com.galgothstudio.backend.project.thumbnail;

import com.galgothstudio.backend.asset.AssetStorageService;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import com.galgothstudio.backend.project.persistence.MobEntity;
import com.galgothstudio.backend.project.persistence.MobRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pipeline de thumbnails (ticket 023, Diseño técnico §8) -- el thumbnail
 * es un asset DERIVADO, nunca parte de la transacción del commit que lo
 * dispara (Guardar, HU-09/ticket 020): si la subida falla, el commit
 * YA se completó y no se revierte -- esta clase ni siquiera se entera de
 * si el commit tuvo éxito, el frontend la invoca DESPUÉS,
 * independientemente (ver `EditorToolbar.vue`).
 *
 * Key fija por mob (`mobs/{mobId}/thumbnail.png`, sobrescrita en cada
 * generación) -- una escritura S3 es atómica (o se completa entera o
 * falla sin tocar el objeto anterior), así que un fallo a mitad de
 * subida conserva el thumbnail previo automáticamente, sin lógica
 * adicional (AC: "se conserva el thumbnail anterior").
 */
@Service
public class ThumbnailService {

	private static final String CONTENT_TYPE_PNG = "image/png";

	private final MobRepository mobRepository;
	private final AssetStorageService assetStorageService;

	public ThumbnailService(MobRepository mobRepository, AssetStorageService assetStorageService) {
		this.mobRepository = mobRepository;
		this.assetStorageService = assetStorageService;
	}

	private static String keyFor(UUID mobId) {
		return "mobs/" + mobId + "/thumbnail.png";
	}

	/** Ruta relativa servible por la propia API -- lo que se guarda en `mobs.thumbnail_key` y lo que el frontend antepone a su `VITE_API_BASE_URL`. Nunca la key interna de S3 (ese es un detalle de `AssetStorageService`). */
	private static String servablePathFor(UUID mobId) {
		return "/api/mobs/" + mobId + "/thumbnail";
	}

	@Transactional
	public void upload(UUID mobId, byte[] pngBytes) {
		MobEntity mob = mobRepository.findById(mobId).orElseThrow(() -> new MobNotFoundException(mobId));
		assetStorageService.put(keyFor(mobId), pngBytes, CONTENT_TYPE_PNG);
		mob.setThumbnailKey(servablePathFor(mobId));
		mob.setUpdatedAt(Instant.now());
		mobRepository.save(mob);
	}

	@Transactional(readOnly = true)
	public Optional<byte[]> download(UUID mobId) {
		if (mobRepository.findById(mobId).isEmpty()) {
			throw new MobNotFoundException(mobId);
		}
		return assetStorageService.get(keyFor(mobId));
	}

}
