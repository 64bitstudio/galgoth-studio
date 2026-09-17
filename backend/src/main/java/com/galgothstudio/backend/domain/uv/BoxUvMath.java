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
		int x = scaledAxis(cuboid.from().x(), cuboid.to().x(), factor);
		int y = scaledAxis(cuboid.from().y(), cuboid.to().y(), factor);
		int z = scaledAxis(cuboid.from().z(), cuboid.to().z(), factor);
		return new Footprint(2 * (x + z), z + y);
	}

	/**
	 * Tamaño de un eje YA EN TEXELS -- ticket 118. El orden de las dos
	 * operaciones no es un detalle: antes se redondeaba a unidades enteras y
	 * recién después se multiplicaba por la densidad
	 * ({@code round(v) * factor}), así que la densidad no podía recuperar lo
	 * que el redondeo ya había destruido. Un eje de 0,4 unidades daba
	 * {@code round(0,4) * 4 = 0}: cara degenerada a CUALQUIER densidad,
	 * justo lo contrario de lo que promete subirla (ticket 109). Uno de 1,4
	 * daba 4 texels en vez de los 6 que le corresponden.
	 *
	 * <p>Medido: 32 caras degeneradas en el reporte de calidad del benchmark
	 * (110) y 22 caras enteramente negras en la verificación en vivo del
	 * 114, 12 de ellas de 4x4 y ninguna mayor a 16x8 -- todas caras chicas.
	 *
	 * <p><b>Sin piso mínimo, y es deliberado</b> (la primera versión del 118
	 * forzaba 1 texel para un eje que redondeaba a cero; se revirtió por dos
	 * razones concretas, no por prudencia genérica):
	 * <ul>
	 *   <li>A {@link TexelDensity#X1} habría CAMBIADO el layout de los
	 *       modelos existentes con ejes menores a 0,5 -- justo lo que
	 *       {@link StableUvStrategy} existe para evitar, porque mover un
	 *       footprint desplaza el packing entero y desalinea la textura ya
	 *       pintada.</li>
	 *   <li>El frontend tiene su propia copia de esta matemática
	 *       ({@code frontend/src/domain/autoUv.ts}, siempre a X1) y un piso
	 *       acá la habría hecho divergir en exactamente esos cuboids.</li>
	 * </ul>
	 * Un eje que aun escalado redondea a cero queda en cero y el reporte de
	 * calidad lo cuenta como cara degenerada: mostrarlo es mejor que taparlo
	 * con un texel inventado.
	 *
	 * <p>Así, a {@link TexelDensity#X1} el resultado es EXACTAMENTE el
	 * anterior ({@code round(v * 1)} es {@code round(v)}) y las fixtures de
	 * los tickets 006/007 siguen valiendo sin tocarlas.
	 */
	public static int scaledAxis(double from, double to, int texelsPerUnit) {
		return (int) Math.round(Math.abs(to - from) * texelsPerUnit);
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
		// Ticket 118: MISMO cálculo que footprintOf, vía scaledAxis. Las dos
		// rutas lo hacen por separado (una reserva, la otra coloca) y tienen
		// que coincidir exactamente o el packing deja de ser consistente.
		int x = scaledAxis(cuboid.from().x(), cuboid.to().x(), factor);
		int y = scaledAxis(cuboid.from().y(), cuboid.to().y(), factor);
		int z = scaledAxis(cuboid.from().z(), cuboid.to().z(), factor);

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
