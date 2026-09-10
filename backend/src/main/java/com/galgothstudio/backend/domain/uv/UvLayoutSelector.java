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
 * {@link UvLayoutStrategy}): si {@code previousLayout.regions()} no
 * contiene ningún {@code PAINTED}/{@code ORPHAN} → {@link AlphaAutoPackStrategy};
 * si contiene al menos uno → {@link StableUvStrategy}.
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
		boolean hasPaintedOrOrphan = previousLayout.regions()
				.stream()
				.anyMatch(region -> region.status() == UvRegionStatus.PAINTED || region.status() == UvRegionStatus.ORPHAN);
		return hasPaintedOrOrphan ? stableUvStrategy : alphaAutoPackStrategy;
	}

}
