package com.galgothstudio.backend.domain.export.bbmodel;

import java.util.List;

/** Referencia a un {@link BBGroup} por su {@code uuid} más sus hijos anidados (cuboids y/o sub-bones). */
public record BBOutlinerGroupRef(String uuid, boolean isOpen, List<BBOutlinerEntry> children) implements BBOutlinerEntry {
}
