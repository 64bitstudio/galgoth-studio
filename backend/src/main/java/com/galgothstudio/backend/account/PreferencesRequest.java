package com.galgothstudio.backend.account;

/**
 * Ticket 091 -- las 3 preferencias siempre explícitas, nunca parciales
 * (mismo criterio que {@code RenameProjectRequest.description}, ticket
 * 073): el caller reenvía el estado completo de los 3 toggles en cada
 * `PATCH`, aunque solo uno haya cambiado.
 */
public record PreferencesRequest(boolean notifyEmail, boolean notifyProductNews, boolean notifySaveReminders) {
}
