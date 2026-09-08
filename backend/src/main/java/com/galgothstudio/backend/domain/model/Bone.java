package com.galgothstudio.backend.domain.model;

public record Bone(String id, String name, String parentId, Vec3 pivot, Vec3 rotation) {
}
