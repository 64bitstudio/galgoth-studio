package com.galgothstudio.backend.domain.model;

/**
 * Tombstone explícito de espacio de atlas abandonado que NO puede
 * describirse como {@link UvRegion} porque ya no corresponde a ningún
 * {@code (cuboidId, face)} vivo o vigente -- típicamente el {@code rect}
 * que un cuboid {@code PAINTED} ocupaba antes de un resize confirmado que
 * lo reubicó (ticket 040, Diseño técnico §1 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`).
 *
 * {@code sourceCuboidId}/{@code sourceFace} son solo trazabilidad para
 * debug/QA, no se usan para resolver ocupación (eso lo hace {@code rect}).
 *
 * Sin lógica de negocio todavía que cree/consuma reservas -- eso es
 * `StableUvStrategy` (ticket 041). Este ticket es solo el contrato de datos.
 */
public record UvReservation(
		String id,
		Vec4 rect,
		UvReservationReason reason,
		String sourceCuboidId,
		FaceName sourceFace) {
}
