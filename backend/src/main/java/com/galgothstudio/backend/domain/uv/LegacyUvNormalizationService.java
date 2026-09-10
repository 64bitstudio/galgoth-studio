package com.galgothstudio.backend.domain.uv;

import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.UvRegionStatus;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Migración explícita para revisiones legacy de Fase 1+2 -- ticket 044,
 * Diseño técnico §3 de `docs/definiciones/galgoth-studio-fase3-textura.md`.
 *
 * <p>Antes de este ticket, {@code BBModelExporterV5}/{@code V4} SIEMPRE
 * recomputaban la UV en cada export (ticket 011) -- nunca hubo garantía
 * fuerte de que el {@code UvLayout} efectivamente ALMACENADO en una
 * revisión legacy coincida byte a byte con lo que {@link AlphaAutoPackStrategy}
 * produciría hoy contra la misma geometría (implementaciones de
 * box-unwrap pueden haber tenido ajustes menores entre iteraciones de
 * 006/007). Ahora que el exportador nunca recomputa nada (ticket 044,
 * punto 1), este servicio es el único lugar que decide si hace falta
 * normalizar -- invocado por el caller EXPLÍCITAMENTE, ANTES del
 * exportador, nunca dentro de él.
 *
 * <p>Es un no-op salvo que se cumplan AMBAS condiciones:
 * <ol>
 * <li>{@code model.uv().regions()} no contiene ningún {@code PAINTED}/
 * {@code ORPHAN} -- condición de seguridad (garantiza que no hay nada
 * pintado que perder), no una heurística.</li>
 * <li>el {@code UvLayout} almacenado difiere ESTRUCTURALMENTE de lo que
 * {@link AlphaAutoPackStrategy#layout(List, int, int)} calcularía hoy
 * contra esa misma geometría.</li>
 * </ol>
 *
 * <p>Cuando ambas se cumplen, el {@code UvLayout} recalculado es
 * TRANSITORIO -- solo vive en el {@link MobProjectModel} devuelto, en
 * memoria, para esa llamada de export. Nunca se persiste de vuelta a la
 * Revision (las revisiones son inmutables por diseño, invariante
 * establecida en tickets anteriores -- no se reabre acá).
 *
 * <p>Si la geometría de una revisión legacy ya no cupiera en el atlas
 * almacenado según el algoritmo vigente (edge case, no cubierto por
 * ningún AC de este ticket), {@link AlphaAutoPackStrategy#layout(List, int, int)}
 * propaga {@link UvAtlasOverflowException} tal cual -- no se captura ni se
 * oculta acá; ver Hecho del ticket 044.
 */
@Service
public class LegacyUvNormalizationService {

	private final AlphaAutoPackStrategy alphaAutoPackStrategy;

	public LegacyUvNormalizationService(AlphaAutoPackStrategy alphaAutoPackStrategy) {
		this.alphaAutoPackStrategy = alphaAutoPackStrategy;
	}

	public MobProjectModel normalizeIfSafe(MobProjectModel model) {
		if (hasPaintedOrOrphanRegions(model)) {
			return model;
		}

		int width = model.texture().width();
		int height = model.texture().height();
		UvLayoutStrategy.Result recomputed = alphaAutoPackStrategy.layout(model.cuboids(), width, height);

		if (matchesStored(model.uv().regions(), recomputed.regions())) {
			return model;
		}

		UvLayout normalizedUv = new UvLayout(width, height, recomputed.regions(), model.uv().reservations());
		return new MobProjectModel(
				model.mobId(), model.projectId(), model.name(), model.baseType(), model.units(), model.bones(),
				recomputed.cuboids(), model.texture(), normalizedUv, model.animations(), model.exportSettings(),
				model.referenceImages());
	}

	private static boolean hasPaintedOrOrphanRegions(MobProjectModel model) {
		return model.uv()
				.regions()
				.stream()
				.anyMatch(region -> region.status() == UvRegionStatus.PAINTED || region.status() == UvRegionStatus.ORPHAN);
	}

	/** "Difiere estructuralmente" -- mismo conjunto de (cuboidId, face, rect, status), sin importar el orden. */
	private static boolean matchesStored(List<UvRegion> stored, List<UvRegion> recomputed) {
		return Set.copyOf(stored).equals(Set.copyOf(recomputed));
	}

}
