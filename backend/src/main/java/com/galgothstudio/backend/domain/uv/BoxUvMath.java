package com.galgothstudio.backend.domain.uv;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.Vec4;

/**
 * Matemática de box-unwrap/footprint (ticket 041, Diseño técnico §2 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`) -- extraída de
 * {@link AlphaAutoPackStrategy} (única implementación hasta este ticket)
 * para que {@link StableUvStrategy} la reutilice sin duplicar ni divergir
 * del cálculo ya verificado (ticket 006/007, fixture compartida
 * `contracts/fixtures/uv-layout-fixture.json`).
 *
 * <p><b>Desenvolvimiento de caja (box UV unwrap):</b> para un cuboid con
 * tamaño {@code (x, y, z)} (ancho, alto, profundidad), las 6 caras se
 * desenvuelven en una cruz de {@code 2*(x+z)} de ancho por {@code (z+y)}
 * de alto -- fórmula verificada contra la implementación real de
 * Blockbench (modo "Box UV", `js/outliner/types/cube.js`/`js/uv/uv.js`
 * del repo `JannisX11/blockbench`, no inventada):
 * <pre>
 *        +---+---+
 *        |up |dwn|      fila superior: alto z
 *   +---+---+---+---+
 *   | w | n | e | s |   fila inferior: alto y
 *   +---+---+---+---+
 *     z   x   z   x
 * </pre>
 */
public final class BoxUvMath {

	/** Único atlas de textura que existe este ciclo -- ver Face#texture(). */
	private static final int SINGLE_TEXTURE_INDEX = 0;

	private BoxUvMath() {
	}

	public record Footprint(int width, int height) {
	}

	/**
	 * Dimensiones (en píxeles de textura) del footprint de box-unwrap de un
	 * cuboid, a densidad de texel {@link TexelDensity#X1} (1 texel por
	 * unidad de modelo) -- sobrecarga pre-042 que se conserva sin cambios
	 * de comportamiento para {@link AlphaAutoPackStrategy}/{@link StableUvStrategy}
	 * (tickets 006/007/041), que siguen sin recibir densidad como
	 * parámetro. Delega en {@link #footprintOf(Cuboid, TexelDensity)}.
	 */
	public static Footprint footprintOf(Cuboid cuboid) {
		return footprintOf(cuboid, TexelDensity.X1);
	}

	/**
	 * Dimensiones (en píxeles de textura) del footprint de box-unwrap de un
	 * cuboid a la {@code density} de texel dada -- ticket 042, Diseño
	 * técnico §7 de `docs/definiciones/galgoth-studio-fase3-textura.md`:
	 * cada eje del cuboid (en unidades de modelo) se multiplica por
	 * {@link TexelDensity#texelsPerUnit()} ANTES de armar la cruz de
	 * box-unwrap -- a densidad {@code X2}, el mismo cuboid produce un
	 * footprint del DOBLE de tamaño lineal en cada eje (nunca "el mismo
	 * layout UV al doble de resolución").
	 */
	public static Footprint footprintOf(Cuboid cuboid, TexelDensity density) {
		int factor = density.texelsPerUnit();
		int x = boxSizeAxis(cuboid.from().x(), cuboid.to().x()) * factor;
		int y = boxSizeAxis(cuboid.from().y(), cuboid.to().y()) * factor;
		int z = boxSizeAxis(cuboid.from().z(), cuboid.to().z()) * factor;
		return new Footprint(2 * (x + z), z + y);
	}

	public static int boxSizeAxis(double from, double to) {
		return (int) Math.round(Math.abs(to - from));
	}

	/**
	 * Coloca las 6 caras del box-unwrap de {@code cuboid} con su esquina
	 * superior-izquierda en {@code (offsetX, offsetY)}, a densidad de
	 * texel {@link TexelDensity#X1} -- sobrecarga pre-042 que se conserva
	 * sin cambios de comportamiento para {@link AlphaAutoPackStrategy}
	 * (tickets 006/007/041, contrato {@link UvLayoutStrategy}). Delega en
	 * {@link #boxUnwrapFaces(Cuboid, int, int, TexelDensity)}.
	 */
	public static CuboidFaces boxUnwrapFaces(Cuboid cuboid, int offsetX, int offsetY) {
		return boxUnwrapFaces(cuboid, offsetX, offsetY, TexelDensity.X1);
	}

	/**
	 * Igual que {@link #boxUnwrapFaces(Cuboid, int, int)}, pero cada eje
	 * del cuboid se multiplica por {@link TexelDensity#texelsPerUnit()}
	 * ANTES de armar la cruz -- a densidad {@code X2}, cada cara resultante
	 * mide el doble en cada dimensión (ticket 042, AC: cara física de 8×8
	 * unidades → región de 16×16 texels). {@code offsetX}/{@code offsetY}
	 * ya se interpretan en texels del atlas final (no en unidades de
	 * modelo), así que no se escalan.
	 */
	public static CuboidFaces boxUnwrapFaces(Cuboid cuboid, int offsetX, int offsetY, TexelDensity density) {
		int factor = density.texelsPerUnit();
		int x = boxSizeAxis(cuboid.from().x(), cuboid.to().x()) * factor;
		int y = boxSizeAxis(cuboid.from().y(), cuboid.to().y()) * factor;
		int z = boxSizeAxis(cuboid.from().z(), cuboid.to().z()) * factor;

		Face up = faceAt(offsetX + z, offsetY, x, z);
		Face down = faceAt(offsetX + z + x, offsetY, x, z);
		Face west = faceAt(offsetX, offsetY + z, z, y);
		Face north = faceAt(offsetX + z, offsetY + z, x, y);
		Face east = faceAt(offsetX + z + x, offsetY + z, z, y);
		Face south = faceAt(offsetX + 2 * z + x, offsetY + z, x, y);

		return new CuboidFaces(north, south, east, west, up, down);
	}

	private static Face faceAt(int u0, int v0, int width, int height) {
		return new Face(new Vec4(u0, v0, (double) u0 + width, (double) v0 + height), SINGLE_TEXTURE_INDEX);
	}

	public static Face faceOf(CuboidFaces faces, FaceName name) {
		return switch (name) {
			case NORTH -> faces.north();
			case SOUTH -> faces.south();
			case EAST -> faces.east();
			case WEST -> faces.west();
			case UP -> faces.up();
			case DOWN -> faces.down();
		};
	}

}
