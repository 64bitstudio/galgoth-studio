package com.galgothstudio.backend.domain.uv;

import com.galgothstudio.backend.domain.model.FaceName;
import java.util.List;

/**
 * Error de dominio {@code PAINTED_REGION_RESIZE_CONFIRMATION_REQUIRED}
 * (ticket 041, Diseño técnico §2 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`): un resize
 * cambiaría el footprint de al menos una cara ya {@code PAINTED} --
 * {@link StableUvStrategy} lanza esta excepción SIN mutar nada; solo se
 * aplica el resize (con reempaquetado + {@code UvReservation} del rect
 * abandonado) tras una confirmación explícita del caller
 * ({@code confirmPaintLoss=true}).
 *
 * <p>Misma familia/estilo que {@link UvAtlasOverflowException}: nunca se
 * decide en silencio, el caller (endpoint manual o el flujo de "Aplicar
 * cambios" de la IA, ver Diseño técnico §2) es quien confirma.
 */
public final class PaintedRegionResizeConfirmationRequiredException extends RuntimeException {

	private final List<AffectedFace> affectedFaces;

	/** Una cara {@code PAINTED} de un cuboid vivo cuyo footprint cambiaría con el resize propuesto. */
	public record AffectedFace(String cuboidId, FaceName face) {
	}

	public PaintedRegionResizeConfirmationRequiredException(List<AffectedFace> affectedFaces) {
		super(
				"PAINTED_REGION_RESIZE_CONFIRMATION_REQUIRED: el resize cambiaría el footprint de "
						+ affectedFaces.size() + " cara(s) ya pintada(s): " + affectedFaces);
		this.affectedFaces = List.copyOf(affectedFaces);
	}

	public List<AffectedFace> affectedFaces() {
		return affectedFaces;
	}

}
