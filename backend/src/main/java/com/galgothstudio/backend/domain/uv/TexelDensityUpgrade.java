package com.galgothstudio.backend.domain.uv;

import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegionStatus;

/**
 * Upgrade explícito de densidad de texel (p. ej. {@code X1} → {@code X2})
 * sobre un mob que YA tiene geometría -- ticket 042, Diseño técnico §7 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`, Estado A ("ANTES
 * de que exista contenido PAINTED"): recalcula TODOS los footprints a la
 * nueva densidad y reempaqueta desde cero -- nunca automático, siempre
 * disparado explícitamente (p. ej. desde el paso de Configuración del
 * wizard).
 *
 * <p><b>Estado B (Diseño técnico §7)</b>: en cuanto existe al menos una
 * {@code UvRegion} con {@code status == PAINTED}, la densidad de texel Y
 * {@code TextureDocument.width}/{@code height} quedan CONGELADOS --
 * ninguna operación puede cambiarlos, ni siquiera este upgrade explícito.
 * {@link #upgradeTo} detecta esa condición y devuelve
 * {@link Result#applied()} en {@code false} con el modelo de entrada
 * intacto, en vez de aplicar el cambio en silencio o lanzar una
 * excepción -- "la operación no tiene efecto", tal como pide el AC de
 * ticket, dejando la decisión de cómo comunicarlo (UI deshabilitada/
 * informativa) al caller.
 *
 * <p>Reflow completo antes de {@code PAINTED} es siempre seguro vía
 * {@link AlphaAutoPackStrategy} -- antes de que exista contenido pintado
 * no hay nada que preservar (misma regla de decisión que
 * {@link UvLayoutSelector}, invocada acá directo porque esta clase es
 * estática/sin dependencias Spring). Sin canal HTTP formal todavía
 * (mismo caso que {@code confirmPaintLoss} de {@link StableUvStrategy},
 * ticket 041) -- wiring de un ticket posterior; este es el punto de
 * entrada real para quien sí lo tenga disponible y para los tests de
 * este ticket.
 */
public final class TexelDensityUpgrade {

	private TexelDensityUpgrade() {
	}

	/** @param model el modelo resultante (sin cambios si {@code applied} es {@code false}). */
	public record Result(MobProjectModel model, boolean applied) {
	}

	public static Result upgradeTo(MobProjectModel model, TexelDensity newDensity) {
		if (hasPaintedContent(model)) {
			return new Result(model, false);
		}

		AtlasResolutionCalculator.AtlasSize atlas = AtlasResolutionCalculator.computeAtlas(model.cuboids(), newDensity);
		// Empaquetado REAL a la nueva densidad (no solo el tamaño de atlas):
		// BoxUvMath.footprintOf/boxUnwrapFaces escalan cada eje por
		// newDensity.texelsPerUnit(), así que las UvRegion resultantes son
		// efectivamente más grandes en texels -- el upgrade x1->x2 aumenta
		// la resolución real por cara, no solo el lienzo que las contiene.
		UvLayoutStrategy.Result packResult = new AlphaAutoPackStrategy().layout(model.cuboids(), atlas.width(), atlas.height(), newDensity);

		MobProjectModel upgraded = new MobProjectModel(
				model.mobId(), model.projectId(), model.name(), model.baseType(), model.units(), model.bones(),
				packResult.cuboids(), new TextureDocument(atlas.width(), atlas.height(), model.texture().storageKey()),
				new UvLayout(atlas.width(), atlas.height(), packResult.regions(), packResult.reservations()),
				model.animations(), model.exportSettings(), model.referenceImages());
		return new Result(upgraded, true);
	}

	private static boolean hasPaintedContent(MobProjectModel model) {
		return model.uv().regions().stream().anyMatch(region -> region.status() == UvRegionStatus.PAINTED);
	}

}
