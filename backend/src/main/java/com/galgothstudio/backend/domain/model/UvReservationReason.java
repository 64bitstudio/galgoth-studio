package com.galgothstudio.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Motivo de una {@link UvReservation} -- ticket 040, Diseño técnico §1 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`. Único valor este
 * ciclo: el {@code rect} que una cara {@code PAINTED} ocupaba antes de un
 * resize destructivo confirmado que la reubicó.
 */
public enum UvReservationReason {
	@JsonProperty("resize_abandoned") RESIZE_ABANDONED

}
