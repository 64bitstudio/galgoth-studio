package com.galgothstudio.backend.domain.export.bbmodel;

import com.galgothstudio.backend.domain.model.Vec3;

/**
 * Entrada del array plano {@code groups} (formato v5) -- SIN {@code children}
 * (la jerarquía vive únicamente en {@code outliner}, ver {@link BBOutlinerGroupRef}).
 * A diferencia de {@link BBElement}, `origin`/`rotation` de un group SIEMPRE
 * se escriben explícitos, incluso en [0,0,0] -- verificado en
 * {@code Group.getChildlessCopy()} del código fuente real de Blockbench
 * (asigna ambos incondicionalmente, sin el guard `allEqual(0)` que sí tiene
 * `Cube.getSaveCopy()`).
 */
public record BBGroup(String name, String uuid, Vec3 origin, Vec3 rotation, boolean export, boolean isOpen) {
}
