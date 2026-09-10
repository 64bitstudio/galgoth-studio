package com.galgothstudio.backend.domain.model;

import java.util.List;

/**
 * Bookkeeping del atlas a nivel de mob -- lo puebla AutoUv (ticket
 * 006/007), no este ticket. La UV real por cara ya vive en
 * {@link Cuboid#faces()}[x].uv; esto es el índice de qué región del
 * atlas está ocupada, para que el packing no genere overlaps.
 *
 * {@code reservations} es un campo aditivo (ticket 040, Diseño técnico
 * §1 de `docs/definiciones/galgoth-studio-fase3-textura.md`): tombstones
 * explícitos de espacio de atlas abandonado por un resize destructivo
 * confirmado, que no corresponden a ninguna fila viva de {@code regions}.
 * Compatibilidad legacy explícita: una revisión de Fase 1+2 no tiene este
 * campo en su JSON almacenado -- el constructor compacto normaliza
 * {@code null} (lo que Jackson deja al deserializar un JSON sin la
 * propiedad) a una lista vacía sin lanzar excepción y sin migración de
 * datos (AC del ticket 040).
 */
public record UvLayout(int textureWidth, int textureHeight, List<UvRegion> regions, List<UvReservation> reservations) {

	public UvLayout {
		if (reservations == null) {
			reservations = List.of();
		}
	}

	/**
	 * Constructor de conveniencia para el código pre-040 que todavía crea
	 * layouts sin reservas (el caso normal fuera del flujo de resize
	 * destructivo de `StableUvStrategy`, ticket 041, sin cambios este
	 * ticket). Delega en una lista vacía.
	 */
	public UvLayout(int textureWidth, int textureHeight, List<UvRegion> regions) {
		this(textureWidth, textureHeight, regions, List.of());
	}

}
