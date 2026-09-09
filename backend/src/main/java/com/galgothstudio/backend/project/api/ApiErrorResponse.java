package com.galgothstudio.backend.project.api;

import java.util.List;

/** Forma uniforme de error para toda la API REST (ticket 020, primer controlador del proyecto -- establece la convención para 021+). */
public record ApiErrorResponse(String error, String message, List<String> details) {
}
