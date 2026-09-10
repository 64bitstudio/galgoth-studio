package com.galgothstudio.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code status} es un campo aditivo (ticket 040, Diseño técnico §1 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`). Compatibilidad
 * legacy explícita: una revisión de Fase 1+2 no tiene este campo en su
 * JSON almacenado -- el constructor compacto normaliza {@code null} (lo
 * que Jackson deja al deserializar un JSON sin la propiedad) a
 * {@link UvRegionStatus#UNPAINTED} sin lanzar excepción y sin migración de
 * datos (AC del ticket 040).
 *
 * <p>{@code paintedBy} (ticket 054, ver {@link UvPaintOrigin}) es aditivo
 * de la misma forma, pero a diferencia de {@code status} SÍ puede quedar
 * {@code null} legítimamente en el estado normal (cualquier región
 * {@code UNPAINTED}/{@code ORPHAN}, o `PAINTED` de origen desconocido) --
 * {@code @JsonInclude(NON_NULL)} en ese único campo evita que se
 * serialice como `"paintedBy":null` explícito en TODA región existente,
 * lo que habría roto la comparación JSON estricta byte-a-byte de
 * {@code MobProjectModelRoundTripTest}/fixtures ya congeladas contra
 * revisiones de Fase 1+2 y de Fase 3 pre-054 (ninguna las tiene). Mismo
 * mecanismo que Jackson ya soporta sobre un componente de record
 * individual, sin afectar `status`/otros campos.
 */
public record UvRegion(
		String cuboidId, FaceName face, Vec4 rect, UvRegionStatus status, @JsonInclude(JsonInclude.Include.NON_NULL) UvPaintOrigin paintedBy) {

	public UvRegion {
		if (status == null) {
			status = UvRegionStatus.UNPAINTED;
		}
	}

	/**
	 * Constructor de conveniencia para el código pre-054 completo que todavía
	 * crea regiones sin opinión de {@link UvPaintOrigin} (ticket 040 y
	 * anteriores) -- {@code paintedBy=null}, tratado como "origen
	 * desconocido/posible pintado a mano" por quien lo interprete (ver
	 * {@link UvPaintOrigin}, Javadoc de clase). Delega en el constructor
	 * canónico de 5 argumentos -- cero cambio de forma para ningún call
	 * site existente (`AlphaAutoPackStrategy`, `StableUvStrategy`,
	 * `TextureGenerationSheetPlanner`, fixtures de test, etc.).
	 */
	public UvRegion(String cuboidId, FaceName face, Vec4 rect, UvRegionStatus status) {
		this(cuboidId, face, rect, status, null);
	}

	/**
	 * Constructor de conveniencia para el código pre-040 que todavía crea
	 * regiones sin opinión de {@code status} (siempre nuevas, nunca
	 * pintadas todavía) -- p. ej. {@code AlphaAutoPackStrategy}, que queda
	 * sin cambios este ticket. Delega en {@link UvRegionStatus#UNPAINTED}.
	 */
	public UvRegion(String cuboidId, FaceName face, Vec4 rect) {
		this(cuboidId, face, rect, UvRegionStatus.UNPAINTED, null);
	}

}
