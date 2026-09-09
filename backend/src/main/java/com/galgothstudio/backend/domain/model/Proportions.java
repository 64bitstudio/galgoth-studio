package com.galgothstudio.backend.domain.model;

/** Los 4 campos exactos del ejemplo literal del master prompt §9.1 -- ver `contracts/schemas/model-intent.schema.json`. */
public record Proportions(double headScale, double armLength, double handScale, double shoulderWidth) {
}
