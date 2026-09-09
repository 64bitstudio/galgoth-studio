package com.galgothstudio.backend.project.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Mapea `mob_revisions` (ticket 003) -- historial append-only e
 * INMUTABLE (nunca se actualiza una fila ya insertada). `revision_number`
 * es único por mob, no global (`UNIQUE(mob_id, revision_number)`).
 * Mismo criterio de `modelJson` que {@link MobDraftEntity}.
 */
@Entity
@Table(name = "mob_revisions")
public class MobRevisionEntity {

	@Id
	private UUID id;

	@Column(name = "mob_id", nullable = false)
	private UUID mobId;

	@Column(name = "revision_number", nullable = false)
	private int revisionNumber;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "model_jsonb", nullable = false, columnDefinition = "jsonb")
	private String modelJson;

	@Column(name = "created_by", nullable = false)
	private String createdBy;

	@Column(name = "change_summary")
	private String changeSummary;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected MobRevisionEntity() {
		// JPA
	}

	public MobRevisionEntity(
			UUID id, UUID mobId, int revisionNumber, String modelJson, String createdBy, Instant createdAt) {
		this.id = id;
		this.mobId = mobId;
		this.revisionNumber = revisionNumber;
		this.modelJson = modelJson;
		this.createdBy = createdBy;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMobId() {
		return mobId;
	}

	public int getRevisionNumber() {
		return revisionNumber;
	}

	public String getModelJson() {
		return modelJson;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public String getChangeSummary() {
		return changeSummary;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}
