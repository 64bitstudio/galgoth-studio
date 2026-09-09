package com.galgothstudio.backend.project.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapea `reference_images` (ticket 003) -- append-only, INMUTABLE (nunca
 * se actualiza una fila ya insertada), mismo criterio que
 * {@link MobRevisionEntity}. `width`/`height`/`contentType` se derivan
 * SIEMPRE de los bytes reales de la imagen subida (`ReferenceImageService`,
 * ticket 024) -- nunca de un valor provisto por el cliente, mismo
 * criterio de autoridad server-side que `baseType` en `MobService`.
 */
@Entity
@Table(name = "reference_images")
public class ReferenceImageEntity {

	@Id
	private UUID id;

	@Column(name = "mob_id", nullable = false)
	private UUID mobId;

	@Column(name = "storage_key", nullable = false)
	private String storageKey;

	@Column(nullable = false)
	private int width;

	@Column(nullable = false)
	private int height;

	@Column(name = "content_type", nullable = false)
	private String contentType;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected ReferenceImageEntity() {
		// JPA
	}

	public ReferenceImageEntity(
			UUID id, UUID mobId, String storageKey, int width, int height, String contentType, Instant createdAt) {
		this.id = id;
		this.mobId = mobId;
		this.storageKey = storageKey;
		this.width = width;
		this.height = height;
		this.contentType = contentType;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMobId() {
		return mobId;
	}

	public String getStorageKey() {
		return storageKey;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public String getContentType() {
		return contentType;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
