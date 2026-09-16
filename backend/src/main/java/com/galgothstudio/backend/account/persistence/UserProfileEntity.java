package com.galgothstudio.backend.account.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Mapea `user_profile` (ticket 091) -- el "perfil de producto" de cada
 * usuario de auth-core-mc: avatar y preferencias de notificación.
 * {@code userId} es el {@code sub} del JWT, sin FK (mismo criterio que
 * {@code ProjectEntity.ownerRef}, ticket 084).
 */
@Entity
@Table(name = "user_profile")
public class UserProfileEntity {

	@Id
	@Column(name = "user_id")
	private UUID userId;

	@Column(name = "avatar_key")
	private String avatarKey;

	@Column(name = "avatar_content_type")
	private String avatarContentType;

	/**
	 * Hallazgo real de seguridad (auth-core-mc#071): la URL pública del
	 * avatar NUNCA debe llevar {@code userId} (el mismo id de identidad
	 * real que auth-core-mc usa en sus JWTs) -- este id es un identificador
	 * de servicio aparte, sin relación reconstruible con la identidad, que
	 * sí es seguro exponer a cualquier visitante sin sesión (ver
	 * `AccountAvatarController`). Generado una vez, en Java (no confía en
	 * el DEFAULT de la columna para las filas nuevas -- mismo criterio ya
	 * usado en este constructor para las 3 preferencias).
	 */
	@Column(name = "public_avatar_id", nullable = false)
	private UUID publicAvatarId;

	@Column(name = "notify_email", nullable = false)
	private boolean notifyEmail;

	@Column(name = "notify_product_news", nullable = false)
	private boolean notifyProductNews;

	@Column(name = "notify_save_reminders", nullable = false)
	private boolean notifySaveReminders;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected UserProfileEntity() {
		// JPA
	}

	/** Fila nueva -- nace con las 3 preferencias en {@code true} (default de negocio, ticket 091). */
	public UserProfileEntity(UUID userId) {
		this.userId = userId;
		this.publicAvatarId = UUID.randomUUID();
		this.notifyEmail = true;
		this.notifyProductNews = true;
		this.notifySaveReminders = true;
		this.updatedAt = Instant.now();
	}

	public UUID getUserId() {
		return userId;
	}

	public String getAvatarKey() {
		return avatarKey;
	}

	public void setAvatarKey(String avatarKey) {
		this.avatarKey = avatarKey;
	}

	public UUID getPublicAvatarId() {
		return publicAvatarId;
	}

	public String getAvatarContentType() {
		return avatarContentType;
	}

	public void setAvatarContentType(String avatarContentType) {
		this.avatarContentType = avatarContentType;
	}

	public boolean isNotifyEmail() {
		return notifyEmail;
	}

	public void setNotifyEmail(boolean notifyEmail) {
		this.notifyEmail = notifyEmail;
	}

	public boolean isNotifyProductNews() {
		return notifyProductNews;
	}

	public void setNotifyProductNews(boolean notifyProductNews) {
		this.notifyProductNews = notifyProductNews;
	}

	public boolean isNotifySaveReminders() {
		return notifySaveReminders;
	}

	public void setNotifySaveReminders(boolean notifySaveReminders) {
		this.notifySaveReminders = notifySaveReminders;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(Instant updatedAt) {
		this.updatedAt = updatedAt;
	}

}
