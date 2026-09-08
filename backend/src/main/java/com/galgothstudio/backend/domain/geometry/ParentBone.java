package com.galgothstudio.backend.domain.geometry;

/**
 * Reasigna el padre de un bone. Se rechaza (batch completo, atómico) si
 * {@code newParentId} no resuelve a un bone existente, si intenta
 * auto-parentarse, o si crearía un ciclo en la jerarquía.
 *
 * @param target id real, o {@code tempId} de un {@code createBone} anterior en el mismo batch.
 * @param newParentId id real, o {@code tempId} de un {@code createBone} anterior en el mismo
 *                    batch, o {@code null} para convertir el bone en raíz.
 */
public record ParentBone(String target, String newParentId) implements GeometryOperation {
}
