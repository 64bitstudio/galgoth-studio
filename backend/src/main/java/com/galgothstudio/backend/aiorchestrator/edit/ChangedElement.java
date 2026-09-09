package com.galgothstudio.backend.aiorchestrator.edit;

/** Un elemento afectado por un plan de edición (ticket 031, mockup 06: "handRight (nuevo cubo)", "shoulderRight (modificar)"). `type`: "bone"|"cuboid". `changeKind`: "added"|"modified"|"removed". */
public record ChangedElement(String type, String id, String name, String changeKind) {
}
