package com.galgothstudio.backend.domain.export.bbmodel;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Nodo del árbol {@code outliner} (formato v5) -- un array que MEZCLA dos
 * formas: un string uuid suelto para un elemento hoja, o un objeto
 * {@code {uuid, isOpen, children}} para un group -- verificado contra
 * {@code Outliner.toJSON()} del código fuente real de Blockbench
 * (`js/outliner/outliner.js`), no inventado. La jerarquía completa vive
 * SOLO aquí; {@link BBGroup} en el array plano {@code groups} nunca trae
 * {@code children}.
 */
@JsonSerialize(using = BBOutlinerEntrySerializer.class)
public sealed interface BBOutlinerEntry permits BBOutlinerLeaf, BBOutlinerGroupRef {
}
