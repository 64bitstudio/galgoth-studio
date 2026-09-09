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
 * Mapea `mob_drafts` (ticket 003) -- `mob_id` ES la PK (una sola fila
 * mutable por mob, inexistente hasta el primer autosave/Guardar, ver
 * "Estado inicial de un mob" en `docs/definiciones/galgoth-studio-mvp.md`).
 *
 * `modelJson` guarda el `MobProjectModel` ya serializado como texto JSON
 * (vía el `ObjectMapper` de la app, con los módulos Vec3/Vec4 registrados
 * -- ver `JacksonConfig`) -- `@JdbcTypeCode(SqlTypes.JSON)` sobre un
 * campo `String` le dice a Hibernate que el valor YA es JSON serializado
 * y lo escribe tal cual en la columna `jsonb`, sin una segunda pasada de
 * serialización con SU PROPIO ObjectMapper interno (que no conocería los
 * módulos Vec3/Vec4 y produciría un formato distinto al del resto del
 * proyecto). La conversión Java<->JSON vive en `DraftPersistenceService`,
 * no en la entidad.
 */
@Entity
@Table(name = "mob_drafts")
public class MobDraftEntity {

	@Id
	@Column(name = "mob_id")
	private UUID mobId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "draft_model_jsonb", nullable = false, columnDefinition = "jsonb")
	private String modelJson;

	@Column(name = "draft_version", nullable = false)
	private int draftVersion;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected MobDraftEntity() {
		// JPA
	}

	public MobDraftEntity(UUID mobId, String modelJson, int draftVersion, Instant updatedAt) {
		this.mobId = mobId;
		this.modelJson = modelJson;
		this.draftVersion = draftVersion;
		this.updatedAt = updatedAt;
	}

	public UUID getMobId() {
		return mobId;
	}

	public String getModelJson() {
		return modelJson;
	}

	public void setModelJson(String modelJson) {
		this.modelJson = modelJson;
	}

	public int getDraftVersion() {
		return draftVersion;
	}

	public void setDraftVersion(int draftVersion) {
		this.draftVersion = draftVersion;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}
