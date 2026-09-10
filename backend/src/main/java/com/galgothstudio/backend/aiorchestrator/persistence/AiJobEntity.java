package com.galgothstudio.backend.aiorchestrator.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Mapea `ai_jobs` (ticket 003) -- a diferencia de `mob_revisions`
 * (inmutable), esta fila SÍ se muta: se persiste una vez que el
 * pipeline de generación termina, en su estado terminal (`completed`/
 * `failed`), ver `MobGenerationService` para el porqué (ticket 028 es
 * síncrono -- no hay todavía un estado `running` observable, eso es del
 * ticket 029, SSE de progreso).
 *
 * `proposalJson`/`referenceIds` mapean columnas `jsonb` como `String`
 * (serializadas/deserializadas por el caller con el `ObjectMapper` real
 * de la app), mismo criterio que `MobDraftEntity`/`MobRevisionEntity`
 * (ticket 020).
 */
@Entity
@Table(name = "ai_jobs")
public class AiJobEntity {

	@Id
	private UUID id;

	@Column(name = "mob_id", nullable = false)
	private UUID mobId;

	@Column(name = "job_type", nullable = false)
	private String jobType;

	@Column(nullable = false)
	private String status;

	@Column(nullable = false)
	private String provider;

	@Column(nullable = false)
	private String model;

	@Column(name = "prompt_version", nullable = false)
	private String promptVersion;

	@Column(name = "schema_version", nullable = false)
	private String schemaVersion;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "reference_ids", nullable = false, columnDefinition = "jsonb")
	private String referenceIds;

	@Column(name = "base_revision_number")
	private Integer baseRevisionNumber;

	@Column(name = "base_draft_version")
	private Integer baseDraftVersion;

	/** Ticket 054 (V3 migration) -- nullable: NULL para `job_type='generate_texture'` (todos los bones), poblado para `job_type='edit_texture'` (regeneración de un bone puntual, HU-37). */
	@Column(name = "target_bone_id")
	private String targetBoneId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "proposal_jsonb", columnDefinition = "jsonb")
	private String proposalJson;

	@Column
	private String error;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	protected AiJobEntity() {
		// JPA
	}

	public AiJobEntity(UUID id) {
		this.id = id;
	}

	public UUID getId() {
		return id;
	}

	public UUID getMobId() {
		return mobId;
	}

	public void setMobId(UUID mobId) {
		this.mobId = mobId;
	}

	public String getJobType() {
		return jobType;
	}

	public void setJobType(String jobType) {
		this.jobType = jobType;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getProvider() {
		return provider;
	}

	public void setProvider(String provider) {
		this.provider = provider;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getPromptVersion() {
		return promptVersion;
	}

	public void setPromptVersion(String promptVersion) {
		this.promptVersion = promptVersion;
	}

	public String getSchemaVersion() {
		return schemaVersion;
	}

	public void setSchemaVersion(String schemaVersion) {
		this.schemaVersion = schemaVersion;
	}

	public String getReferenceIds() {
		return referenceIds;
	}

	public void setReferenceIds(String referenceIds) {
		this.referenceIds = referenceIds;
	}

	public Integer getBaseRevisionNumber() {
		return baseRevisionNumber;
	}

	public void setBaseRevisionNumber(Integer baseRevisionNumber) {
		this.baseRevisionNumber = baseRevisionNumber;
	}

	public Integer getBaseDraftVersion() {
		return baseDraftVersion;
	}

	public void setBaseDraftVersion(Integer baseDraftVersion) {
		this.baseDraftVersion = baseDraftVersion;
	}

	public String getTargetBoneId() {
		return targetBoneId;
	}

	public void setTargetBoneId(String targetBoneId) {
		this.targetBoneId = targetBoneId;
	}

	public String getProposalJson() {
		return proposalJson;
	}

	public void setProposalJson(String proposalJson) {
		this.proposalJson = proposalJson;
	}

	public String getError() {
		return error;
	}

	public void setError(String error) {
		this.error = error;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public void setStartedAt(Instant startedAt) {
		this.startedAt = startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public void setFinishedAt(Instant finishedAt) {
		this.finishedAt = finishedAt;
	}

}
