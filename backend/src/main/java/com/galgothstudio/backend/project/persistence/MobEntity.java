package com.galgothstudio.backend.project.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapea la tabla `mobs` (ticket 003, `V1__init_schema.sql`). Este ticket
 * (020) solo LEE y ACTUALIZA filas ya existentes (la creación de mobs es
 * del ticket 021, CRUD) -- se mapean todas las columnas de todos modos
 * (no solo `currentRevisionNumber`) para que la entidad sea correcta y
 * reutilizable tal cual cuando 021 la necesite para inserts.
 */
@Entity
@Table(name = "mobs")
public class MobEntity {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false)
	private UUID projectId;

	@Column(nullable = false)
	private String name;

	@Column(name = "base_type", nullable = false)
	private String baseType;

	@Column(nullable = false)
	private String status;

	@Column(name = "current_revision_number", nullable = false)
	private int currentRevisionNumber;

	@Column(name = "thumbnail_key")
	private String thumbnailKey;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected MobEntity() {
		// JPA
	}

	public MobEntity(
			UUID id,
			UUID projectId,
			String name,
			String baseType,
			String status,
			int currentRevisionNumber,
			String thumbnailKey,
			Instant createdAt,
			Instant updatedAt) {
		this.id = id;
		this.projectId = projectId;
		this.name = name;
		this.baseType = baseType;
		this.status = status;
		this.currentRevisionNumber = currentRevisionNumber;
		this.thumbnailKey = thumbnailKey;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public String getName() {
		return name;
	}

	public String getBaseType() {
		return baseType;
	}

	public String getStatus() {
		return status;
	}

	public int getCurrentRevisionNumber() {
		return currentRevisionNumber;
	}

	public void setCurrentRevisionNumber(int currentRevisionNumber) {
		this.currentRevisionNumber = currentRevisionNumber;
	}

	public String getThumbnailKey() {
		return thumbnailKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}
