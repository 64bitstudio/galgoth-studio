package com.galgothstudio.backend.project.export;

import com.galgothstudio.backend.domain.export.validation.ValidationIssue;
import java.util.List;

/**
 * Estado de la pantalla de exportación (ticket 032, HU-19, mockup 11) --
 * antes de exportar nada. `fmmCompatible`/`fmmIssues` se calculan SIEMPRE
 * contra la última revisión GUARDADA (`mob_revisions`, nunca el draft en
 * curso, decisión confirmada explícitamente con el PO) -- son `null`
 * cuando {@code hasSavedRevision} es `false` (nada guardado todavía, nada
 * que validar). {@code hasUnsavedChanges} es `true` cuando el draft
 * difiere de la última revisión (o cuando no existe ninguna revisión
 * todavía pero sí hay un draft) -- dispara las 3 acciones del mockup en
 * vez del único botón "Exportar .bbmodel".
 */
public record ExportStatusView(
		String mobId,
		String mobName,
		boolean hasSavedRevision,
		boolean hasUnsavedChanges,
		Boolean fmmCompatible,
		List<ValidationIssue> fmmIssues) {
}
