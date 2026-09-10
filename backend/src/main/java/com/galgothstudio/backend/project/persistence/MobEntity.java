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

	/** `deletedAt` no nulo == soft-delete (ticket 039, `V2__mobs_soft_delete.sql`) -- mismo criterio que `ProjectEntity.deletedAt` (ticket 021). */
	@Column(name = "deleted_at")
	private Instant deletedAt;

	/**
	 * Sin constructor de todos los campos a propósito (Sonar S107: más de
	 * 7 parámetros) -- estilo JavaBean (constructor vacío + setters), más
	 * idiomático para una entidad JPA de todos modos. `id` no tiene
	 * setter porque nunca cambia tras crearse la fila.
	 */
	protected MobEntity() {
		// JPA
	}

	public MobEntity(UUID id) {
		this.id = id;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProjectId() {
		return projectId;
	}

	public void setProjectId(UUID projectId) {
		this.projectId = projectId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getBaseType() {
		return baseType;
	}

	public void setBaseType(String baseType) {
		this.baseType = baseType;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
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

	public void setThumbnailKey(String thumbnailKey) {
		this.thumbnailKey = thumbnailKey;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}

}
