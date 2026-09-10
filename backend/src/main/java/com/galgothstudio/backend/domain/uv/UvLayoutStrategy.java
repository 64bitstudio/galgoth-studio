package com.galgothstudio.backend.domain.uv;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.UvReservation;
import java.util.List;

/**
 * Asignación de UV para los cuboids de un mob -- ticket 006,
 * `docs/definiciones/galgoth-studio-mvp.md` (Diseño técnico §6 y su
 * Addendum de implementación). Implementaciones: {@link AlphaAutoPackStrategy}
 * (reflow completo, Fase 1+2) y, desde el ticket 041 (Diseño técnico §2 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`), {@link StableUvStrategy}
 * (preserva contenido ya pintado) y {@link UvLayoutSelector} (decide entre
 * ambas).
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

	/**
	 * Sobrecarga aditiva (ticket 041) que recibe el {@link UvLayout} previo
	 * COMPLETO (no solo {@code regions()}) para que una implementación
	 * como {@link StableUvStrategy} pueda leer también {@code reservations()}.
	 * Default: reflow completo, delega en la sobrecarga de 3 argumentos --
	 * {@link AlphaAutoPackStrategy} NO sobreescribe este método (cero
	 * cambios de comportamiento, ticket 041 AC #1).
	 *
	 * @param previousLayout el {@code UvLayout} tal como estaba antes de esta operación.
	 */
	default Result layout(List<Cuboid> cuboids, int textureWidth, int textureHeight, UvLayout previousLayout) {
		return layout(cuboids, textureWidth, textureHeight);
	}

	/**
	 * Sobrecarga aditiva (ticket 043, Diseño técnico §2 de
	 * `docs/definiciones/galgoth-studio-fase3-textura.md`): mismo contrato
	 * que la sobrecarga de 4 argumentos, más el flag explícito de
	 * confirmación de pérdida de pintura del caso "Resize" -- el canal
	 * formal desde {@code POST /api/mobs/{mobId}/geometry/apply}
	 * (`project/geometry/MobGeometryApplyService`) hasta
	 * {@link StableUvStrategy#layout(List, int, int, UvLayout, boolean)},
	 * que el ticket 041 dejó documentado como "wiring de un ticket
	 * posterior". Default: ignora el flag y delega en la sobrecarga de 4
	 * argumentos -- {@link AlphaAutoPackStrategy} nunca lanza
	 * {@link PaintedRegionResizeConfirmationRequiredException} y no
	 * sobreescribe este método, cero cambio de comportamiento.
	 *
	 * @param confirmPaintLoss si {@code true}, un resize que afectaría una
	 *        cara {@code PAINTED} se aplica igual (reempaquetando +
	 *        reservando el rect abandonado) en vez de lanzar la excepción
	 *        de confirmación.
	 */
	default Result layout(
			List<Cuboid> cuboids, int textureWidth, int textureHeight, UvLayout previousLayout, boolean confirmPaintLoss) {
		return layout(cuboids, textureWidth, textureHeight, previousLayout);
	}

	/**
	 * @param reservations tombstones de espacio de atlas abandonado (ticket
	 *        041) -- aditivo respecto al ticket 006/007: {@link AlphaAutoPackStrategy}
	 *        sigue usando el constructor de 2 argumentos (reservations vacío)
	 *        sin cambios.
	 */
	record Result(List<Cuboid> cuboids, List<UvRegion> regions, List<UvReservation> reservations) {

		public Result {
			if (reservations == null) {
				reservations = List.of();
			}
		}

		/** Constructor de conveniencia para el código pre-041 (siempre sin reservas nuevas). */
		public Result(List<Cuboid> cuboids, List<UvRegion> regions) {
			this(cuboids, regions, List.of());
		}
	}

}
