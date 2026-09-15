package com.galgothstudio.backend.project.thumbnail;

import com.galgothstudio.backend.asset.AssetStorageService;
import com.galgothstudio.backend.project.access.ProjectAccessGuard;
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
	private final ProjectAccessGuard projectAccessGuard;

	public ThumbnailService(MobRepository mobRepository, AssetStorageService assetStorageService, ProjectAccessGuard projectAccessGuard) {
		this.mobRepository = mobRepository;
		this.assetStorageService = assetStorageService;
		this.projectAccessGuard = projectAccessGuard;
	}

	private static String keyFor(UUID mobId) {
		return "mobs/" + mobId + "/thumbnail.png";
	}

	/** Ruta relativa servible por la propia API -- lo que se guarda en `mobs.thumbnail_key` y lo que el frontend antepone a su `VITE_API_BASE_URL`. Nunca la key interna de S3 (ese es un detalle de `AssetStorageService`). */
	private static String servablePathFor(UUID mobId) {
		return "/api/mobs/" + mobId + "/thumbnail";
	}

	/** Ticket 085 -- mutación, exige dueño real. */
	@Transactional
	public void upload(UUID mobId, String callerId, byte[] pngBytes) {
		MobEntity mob = mobRepository.findById(mobId).orElseThrow(() -> new MobNotFoundException(mobId));
		projectAccessGuard.requireOwner(mob.getProjectId(), callerId);
		assetStorageService.put(keyFor(mobId), pngBytes, CONTENT_TYPE_PNG);
		mob.setThumbnailKey(servablePathFor(mobId));
		mob.setUpdatedAt(Instant.now());
		mobRepository.save(mob);
	}

	/**
	 * Ticket 085 -- deliberadamente SIN enforcement (a diferencia de
	 * {@link #upload}): el frontend renderiza el thumbnail vía
	 * {@code <img :src="...">} directo (dashboard, tarjetas de
	 * proyecto/mob, pantalla de exportación) -- un `<img>` del navegador
	 * nunca puede mandar `Authorization: Bearer`. Protegerlo rompería el
	 * thumbnail de CUALQUIER proyecto privado para su propio dueño (todos
	 * los proyectos nacen privados, ticket 084). VoBo explícito de Marco:
	 * queda como un asset servido por id no adivinable (mismo criterio de
	 * confianza que una URL firmada), hasta un ticket de seguimiento que
	 * reescriba la carga de imágenes en el frontend (fetch autenticado +
	 * blob URL) -- fuera de alcance de este ticket ("nada de frontend").
	 */
	@Transactional(readOnly = true)
	public Optional<byte[]> download(UUID mobId) {
		if (mobRepository.findById(mobId).isEmpty()) {
			throw new MobNotFoundException(mobId);
		}
		return assetStorageService.get(keyFor(mobId));
	}

}
