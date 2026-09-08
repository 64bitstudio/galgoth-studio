package com.galgothstudio.backend.domain.uv;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.UvRegion;
import java.util.List;

/**
 * Asignación automática de UV para los cuboids de un mob -- ticket 006,
 * `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §6 y su
 * Addendum de implementación). Única implementación de este ciclo:
 * {@link AlphaAutoPackStrategy}.
 *
 * <p>No se acopla a la suposición de que siempre se puede re-empaquetar
 * toda la UV libremente -- una futura {@code StableUvStrategy} (Fase 3,
 * cuando exista textura pintada que la UV no pueda seguir moviendo)
 * implementará esta misma interfaz sin tocar {@link com.galgothstudio.backend.domain.geometry.GeometryEngine}
 * ni {@code MobProjectModel}.
 */
public interface UvLayoutStrategy {

	/**
	 * Recalcula la UV de las 6 caras de CADA cuboid de la lista (función
	 * pura y determinista del conjunto completo de entrada -- no un
	 * parche incremental sobre el layout anterior) y produce el
	 * bookkeeping de {@code uv.regions} correspondiente.
	 *
	 * @param cuboids lista de cuboids del mob, en el mismo orden que en el modelo.
	 * @param textureWidth ancho del atlas (`MobProjectModel.uv().textureWidth()`).
	 * @param textureHeight alto del atlas (`MobProjectModel.uv().textureHeight()`).
	 * @return los cuboids con {@code faces} actualizado y las regiones del atlas.
	 * @throws UvAtlasOverflowException si el conjunto de cuboids no cabe en el atlas actual --
	 *         nunca se agranda la resolución en silencio.
	 */
	Result layout(List<Cuboid> cuboids, int textureWidth, int textureHeight);

	record Result(List<Cuboid> cuboids, List<UvRegion> regions) {
	}

}
