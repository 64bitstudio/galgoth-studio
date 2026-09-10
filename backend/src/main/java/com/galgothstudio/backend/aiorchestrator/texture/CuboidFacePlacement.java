package com.galgothstudio.backend.aiorchestrator.texture;

import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;

/**
 * Una cara de un cuboid dentro de una {@link TextureGenerationSheet} --
 * forma EXACTA fijada por el Diseño técnico §11 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md` (ticket 053).
 *
 * @param cuboidId       id REAL del {@link com.galgothstudio.backend.domain.model.Cuboid} dueño de esta cara.
 * @param face           cara del cuboid.
 * @param sheetRect      {@code [x0,y0,x1,y1]} en píxeles -- posición/tamaño de esta cara DENTRO de la
 *                       imagen temporal generada (layout interno de la sheet, distinto del atlas final).
 *                       Misma convención {@code [a,b,c,d] = [u0,v0,u1,v1]} que {@link com.galgothstudio.backend.domain.model.Face#uv()}/
 *                       {@link com.galgothstudio.backend.domain.model.UvRegion#rect()} -- nunca {@code [x,y,width,height]}.
 * @param atlasUvRect    {@code [x0,y0,x1,y1]} en píxeles del atlas -- footprint UV REAL de esta cara,
 *                       ya resuelto por {@code UvLayoutSelector}/{@code StableUvStrategy} ANTES de que este
 *                       planner corra (leído de {@code MobProjectModel.uv().regions()}, nunca recalculado
 *                       ni reasignado acá) -- destino real del slicing/composición.
 * @param relativeSize   dimensiones {@code [x,y,z]} del cuboid completo (no solo de esta cara) en unidades
 *                       de modelo -- mismo valor para las 6 caras de un mismo cuboid, contexto de escala
 *                       relativa para el prompt de generación.
 * @param orientationHint orientación legible ("front"/"back"/"side"/"top"/"bottom"), resuelta desde
 *                        {@code face} -- ver Javadoc de {@link TextureGenerationSheetPlanner} para el
 *                        alcance exacto (simplificación documentada, no compone la rotación del bone).
 */
public record CuboidFacePlacement(
		String cuboidId, FaceName face, Vec4 sheetRect, Vec4 atlasUvRect, Vec3 relativeSize, String orientationHint) {
}
