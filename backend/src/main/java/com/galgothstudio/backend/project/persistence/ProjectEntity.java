package com.galgothstudio.backend.project.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Mapea `projects` (ticket 003). `deletedAt` no nulo == soft-delete (ticket 021). */
@Entity
@Table(name = "projects")
public class ProjectEntity {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String name;

	@Column(name = "owner_ref")
	private String ownerRef;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected ProjectEntity() {
		// JPA
	}

	public ProjectEntity(UUID id, String name, String ownerRef, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.name = name;
		this.ownerRef = ownerRef;
		this.createdAt = createdAt;
		this.updatedAt = updatedAt;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getOwnerRef() {
		return ownerRef;
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

	public Instant getDeletedAt() {
		return deletedAt;
	}

	public void setDeletedAt(Instant deletedAt) {
		this.deletedAt = deletedAt;
	}

}
