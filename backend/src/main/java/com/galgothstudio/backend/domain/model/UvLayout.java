package com.galgothstudio.backend.domain.model;

import java.util.List;

/**
 * Bookkeeping del atlas a nivel de mob -- lo puebla AutoUv (ticket
 * 006/007), no este ticket. La UV real por cara ya vive en
 * {@link Cuboid#faces()}[x].uv; esto es el índice de qué región del
 * atlas está ocupada, para que el packing no genere overlaps.
 */
public record UvLayout(int textureWidth, int textureHeight, List<UvRegion> regions) {
}
