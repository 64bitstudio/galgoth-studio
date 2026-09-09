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
 * Mapea `ai_job_events` (ticket 003) -- registro APPEND-ONLY de progreso
 * de un job (ticket 029, HU-11): una fila por evento emitido, `seq`
 * asignado en orden estricto DENTRO del mismo job (garantizado porque
 * un solo hilo asíncrono procesa un job dado a la vez, ver
 * `MobGenerationService`). `payloadJson` mapea `payload_jsonb` -- su
 * forma real (`preview_operations` | `preview_snapshot`) viaja como
 * campo `"type"` DENTRO del propio JSON, no como columna separada, ver
 * `GenerationEventView`.
 *
 * <p>El propio `GET /api/jobs/{jobId}/events` (ticket 029) usa
 * `seq` (vía el header `Last-Event-ID` de SSE) para reanudar un stream
 * interrumpido -- por eso esta tabla existe como fuente de verdad
 * persistida, no solo como un log en memoria.
 */
@Entity
@Table(name = "ai_job_events")
public class AiJobEventEntity {

	@Id
	private UUID id;

	@Column(name = "job_id", nullable = false)
	private UUID jobId;

	@Column(nullable = false)
	private int seq;

	@Column(nullable = false)
	private String stage;

	@Column
	private String message;

	@Column(name = "progress_pct")
	private Integer progressPct;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload_jsonb", columnDefinition = "jsonb")
	private String payloadJson;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected AiJobEventEntity() {
		// JPA
	}

	public AiJobEventEntity(UUID id) {
		this.id = id;
	}

	public UUID getId() {
		return id;
	}

	public UUID getJobId() {
		return jobId;
	}

	public void setJobId(UUID jobId) {
		this.jobId = jobId;
	}

	public int getSeq() {
		return seq;
	}

	public void setSeq(int seq) {
		this.seq = seq;
	}

	public String getStage() {
		return stage;
	}

	public void setStage(String stage) {
		this.stage = stage;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public Integer getProgressPct() {
		return progressPct;
	}

	public void setProgressPct(Integer progressPct) {
		this.progressPct = progressPct;
	}

	public String getPayloadJson() {
		return payloadJson;
	}

	public void setPayloadJson(String payloadJson) {
		this.payloadJson = payloadJson;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

}
