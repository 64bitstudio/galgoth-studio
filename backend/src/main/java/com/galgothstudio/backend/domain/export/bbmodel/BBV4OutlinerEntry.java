package com.galgothstudio.backend.domain.export.bbmodel;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Nodo del árbol {@code outliner} del formato v4 -- a diferencia de v5
 * ({@link BBOutlinerEntry}), los groups NO viven en un array separado:
 * cada group embebe sus propios datos completos inline, dentro del mismo
 * árbol que define la jerarquía. Verificado contra un `.bbmodel` real del
 * proyecto (`samples/carcomido_minecraft_cuboids.bbmodel`,
 * `format_version: "4.10"`) -- es la forma que Blockbench 4.x escribía
 * antes de separar `groups`/`outliner` en 5.0 (ver ADR 0001, TECHNICAL_REFERENCES.md).
 */
@JsonSerialize(using = BBV4OutlinerEntrySerializer.class)
public sealed interface BBV4OutlinerEntry permits BBV4OutlinerLeaf, BBV4OutlinerGroup {
}
