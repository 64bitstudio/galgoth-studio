package com.galgothstudio.backend.project.export;

/** Contenido `.bbmodel` (JSON) ya exportado, listo para servir como descarga -- ticket 032. */
public record ExportedFile(String filename, String content) {
}
