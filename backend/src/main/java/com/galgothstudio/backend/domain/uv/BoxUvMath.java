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

	/** Dimensiones (en píxeles de textura) del footprint de box-unwrap de un cuboid. */
	public static Footprint footprintOf(Cuboid cuboid) {
		int x = boxSizeAxis(cuboid.from().x(), cuboid.to().x());
		int y = boxSizeAxis(cuboid.from().y(), cuboid.to().y());
		int z = boxSizeAxis(cuboid.from().z(), cuboid.to().z());
		return new Footprint(2 * (x + z), z + y);
	}

	public static int boxSizeAxis(double from, double to) {
		return (int) Math.round(Math.abs(to - from));
	}

	/** Coloca las 6 caras del box-unwrap de {@code cuboid} con su esquina superior-izquierda en {@code (offsetX, offsetY)}. */
	public static CuboidFaces boxUnwrapFaces(Cuboid cuboid, int offsetX, int offsetY) {
		int x = boxSizeAxis(cuboid.from().x(), cuboid.to().x());
		int y = boxSizeAxis(cuboid.from().y(), cuboid.to().y());
		int z = boxSizeAxis(cuboid.from().z(), cuboid.to().z());

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
