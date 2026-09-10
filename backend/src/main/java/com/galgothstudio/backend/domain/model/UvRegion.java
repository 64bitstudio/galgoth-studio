package com.galgothstudio.backend.domain.model;

/**
 * {@code status} es un campo aditivo (ticket 040, Diseño técnico §1 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`). Compatibilidad
 * legacy explícita: una revisión de Fase 1+2 no tiene este campo en su
 * JSON almacenado -- el constructor compacto normaliza {@code null} (lo
 * que Jackson deja al deserializar un JSON sin la propiedad) a
 * {@link UvRegionStatus#UNPAINTED} sin lanzar excepción y sin migración de
 * datos (AC del ticket 040).
 */
public record UvRegion(String cuboidId, FaceName face, Vec4 rect, UvRegionStatus status) {

	public UvRegion {
		if (status == null) {
			status = UvRegionStatus.UNPAINTED;
		}
	}

	/**
	 * Constructor de conveniencia para el código pre-040 que todavía crea
	 * regiones sin opinión de {@code status} (siempre nuevas, nunca
	 * pintadas todavía) -- p. ej. {@code AlphaAutoPackStrategy}, que queda
	 * sin cambios este ticket. Delega en {@link UvRegionStatus#UNPAINTED}.
	 */
	public UvRegion(String cuboidId, FaceName face, Vec4 rect) {
		this(cuboidId, face, rect, UvRegionStatus.UNPAINTED);
	}

}
