package com.galgothstudio.backend.domain.uv;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegionStatus;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Decide, en cada llamada, si el packing de UV puede recalcularse
 * libremente ({@link AlphaAutoPackStrategy}, Fase 1+2) o si debe
 * preservar contenido pintado ({@link StableUvStrategy}, Fase 3) --
 * ticket 041, Diseño técnico §2 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`.
 *
 * <p>Regla de decisión (única, usada por los 3 llamadores de
 * {@link UvLayoutStrategy}, encapsulada en {@link #requiresStableLayout}):
 * {@link StableUvStrategy} si se cumple CUALQUIERA de -- existe al menos
 * una región {@code PAINTED}, existe al menos una región {@code ORPHAN}, o
 * {@code previousLayout.reservations()} no está vacío; {@link AlphaAutoPackStrategy}
 * únicamente cuando las tres condiciones son falsas a la vez (layout
 * "limpio", idéntico al comportamiento de Fase 1+2).
 *
 * <p><b>Ticket 057 -- hallazgo real corregido, cerrado por el PO.</b> Una
 * revisión anterior de esta regla miraba ÚNICAMENTE {@code regions()}
 * (PAINTED/ORPHAN), ignorando {@code reservations()}. Esto era un bug
 * lógico real: un resize confirmado sobre la ÚNICA región {@code PAINTED}
 * la reempaqueta como {@code UNPAINTED} y crea una {@link
 * com.galgothstudio.backend.domain.model.UvReservation} para el rect
 * abandonado -- si esa era la única región pintada, el layout resultante
 * quedaba con {@code PAINTED=0}, {@code ORPHAN=0} y {@code reservations>0},
 * y la regla anterior elegía {@link AlphaAutoPackStrategy} para el
 * siguiente {@code Add}, que no conoce las reservas y podía reempaquetar
 * libremente ENCIMA del rect reservado -- exactamente lo que {@code
 * UvReservation} existe para impedir. Ver Diseño técnico §2/HU-34 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md` (addendum
 * post-implementación).
 *
 * <p>{@code @Primary}: se inyecta automáticamente donde se pida
 * {@link UvLayoutStrategy} por tipo -- {@code GeometryEngine.apply},
 * {@code GeometryPlannerService}, {@code AiGeometryEditPlannerService}.
 * El exportador (`BBModelExporterV5`/`V4` y sus 3 callers --
 * `MobExportService`, `MobGenerationService`, `GenerationResultService`)
 * queda deliberadamente FUERA de esta lista (ticket 044 lo revisita) --
 * esos 3 callers usan {@code @Qualifier("alphaAutoPackStrategy")} para no
 * heredar este {@code @Primary} por accidente (ver Hallazgo A revertido,
 * Diseño técnico §2/§3).
 */
@Component
@Primary
public final class UvLayoutSelector implements UvLayoutStrategy {

	private final AlphaAutoPackStrategy alphaAutoPackStrategy;
	private final StableUvStrategy stableUvStrategy;

	public UvLayoutSelector(
			@Qualifier("alphaAutoPackStrategy") AlphaAutoPackStrategy alphaAutoPackStrategy,
			StableUvStrategy stableUvStrategy) {
		this.alphaAutoPackStrategy = alphaAutoPackStrategy;
		this.stableUvStrategy = stableUvStrategy;
	}

	@Override
	public Result layout(List<Cuboid> cuboids, int textureWidth, int textureHeight) {
		return layout(cuboids, textureWidth, textureHeight, new UvLayout(textureWidth, textureHeight, List.of(), List.of()));
	}

	@Override
	public Result layout(List<Cuboid> cuboids, int textureWidth, int textureHeight, UvLayout previousLayout) {
		return resolve(previousLayout).layout(cuboids, textureWidth, textureHeight, previousLayout);
	}

	/**
	 * Ticket 043: propaga {@code confirmPaintLoss} a la estrategia resuelta
	 * -- {@link StableUvStrategy} lo consume de verdad (único caso que
	 * puede lanzar {@link PaintedRegionResizeConfirmationRequiredException});
	 * {@link AlphaAutoPackStrategy} lo ignora vía el default de la interfaz
	 * (nunca lo necesita).
	 */
	@Override
	public Result layout(
			List<Cuboid> cuboids, int textureWidth, int textureHeight, UvLayout previousLayout, boolean confirmPaintLoss) {
		return resolve(previousLayout).layout(cuboids, textureWidth, textureHeight, previousLayout, confirmPaintLoss);
	}

	private UvLayoutStrategy resolve(UvLayout previousLayout) {
		return requiresStableLayout(previousLayout) ? stableUvStrategy : alphaAutoPackStrategy;
	}

	/**
	 * Ticket 057: {@code StableUvStrategy} si hay al menos una región
	 * {@code PAINTED}/{@code ORPHAN}, O al menos una {@code UvReservation}
	 * vigente -- ver el hallazgo documentado en el Javadoc de la clase.
	 */
	private boolean requiresStableLayout(UvLayout previousLayout) {
		boolean hasPaintedOrOrphan = previousLayout.regions()
				.stream()
				.anyMatch(region -> region.status() == UvRegionStatus.PAINTED || region.status() == UvRegionStatus.ORPHAN);
		return hasPaintedOrOrphan || !previousLayout.reservations().isEmpty();
	}

}
