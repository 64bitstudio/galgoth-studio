package com.galgothstudio.backend.domain.model;

/**
 * @param uv [u0, v0, u1, v1] en píxeles de textura.
 * @param texture índice del atlas de textura, o null hasta que AutoUv lo asigne (ticket 006).
 */
public record Face(double[] uv, Integer texture) {
}
