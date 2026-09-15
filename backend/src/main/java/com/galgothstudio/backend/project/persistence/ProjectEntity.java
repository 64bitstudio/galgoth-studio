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

	/** Ticket 073 -- opcional, texto libre. `null` es el estado normal de un proyecto recién creado (la creación no la pide todavía). */
	@Column(name = "description")
	private String description;

	@Column(name = "owner_ref")
	private String ownerRef;

	/** Ticket 084 -- 'PRIVATE' o 'PUBLIC' (mismo criterio que `MobEntity.status`: String plano, no un enum JPA). Default 'PRIVATE' a nivel de columna (`V5`); el constructor lo exige explícito para que ningún caller nuevo lo olvide. */
	@Column(name = "visibility", nullable = false)
	private String visibility;

	/** Ticket 086 -- nombre a mostrar en Explorar, denormalizado desde la sesión del frontend al crear (`V7`). `null` si no se envió (ej. un caller que no sea el frontend real, como Postman). */
	@Column(name = "owner_display_name")
	private String ownerDisplayName;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "deleted_at")
	private Instant deletedAt;

	protected ProjectEntity() {
		// JPA
	}

	public ProjectEntity(UUID id, String name, String ownerRef, String visibility, Instant createdAt, Instant updatedAt) {
		this.id = id;
		this.name = name;
		this.ownerRef = ownerRef;
		this.visibility = visibility;
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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getOwnerRef() {
		return ownerRef;
	}

	public String getVisibility() {
		return visibility;
	}

	public void setVisibility(String visibility) {
		this.visibility = visibility;
	}

	public String getOwnerDisplayName() {
		return ownerDisplayName;
	}

	public void setOwnerDisplayName(String ownerDisplayName) {
		this.ownerDisplayName = ownerDisplayName;
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
