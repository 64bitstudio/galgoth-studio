package com.galgothstudio.backend.account;

/**
 * Ticket 091 -- {@code avatarUrl} es {@code null} cuando el usuario no ha
 * subido ninguna foto todavía (nunca una URL rota). Cuando existe, es la
 * ruta relativa servible por esta misma API
 * ({@code /api/account/avatar/{userId}}), mismo criterio que
 * {@code thumbnailKey} (ticket 023).
 */
public record UserProfileResponse(
		String avatarUrl, boolean notifyEmail, boolean notifyProductNews, boolean notifySaveReminders) {
}
