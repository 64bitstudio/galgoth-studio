package com.galgothstudio.backend.domain.export.bbmodel;

/** Referencia directa a un {@link BBElement} por su {@code uuid} (cuboid sin hijos propios). */
public record BBOutlinerLeaf(String uuid) implements BBOutlinerEntry {
}
