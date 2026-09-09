package com.galgothstudio.backend.domain.export.bbmodel;

import com.galgothstudio.backend.domain.model.Vec3;
import java.util.List;

/**
 * Group (bone) embebido DIRECTO dentro del árbol {@code outliner} (v4) --
 * incluye sus propios datos completos, no un stub por uuid como en v5.
 *
 * @param rotation {@code null} cuando es [0,0,0] -- se omite del JSON, igual
 *                 convención observada en el `.bbmodel` real del proyecto
 *                 (ningún group con rotación cero trae la clave `rotation`).
 */
public record BBV4OutlinerGroup(
		String name, String uuid, Vec3 origin, Vec3 rotation, boolean export, boolean isOpen,
		List<BBV4OutlinerEntry> children) implements BBV4OutlinerEntry {
}
