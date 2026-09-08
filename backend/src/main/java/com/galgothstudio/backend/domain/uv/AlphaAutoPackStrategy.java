package com.galgothstudio.backend.domain.uv;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.ArrayList;
import java.util.List;

/**
 * Única implementación de {@link UvLayoutStrategy} este ciclo --
 * *shelf-packing* determinista tipo caja de Minecraft, `docs/definiciones/galgoth-studio-mvp.md`
 * (Diseño técnico §6): objetivo es VALIDEZ, no densidad de atlas (no hay
 * textura real que optimizar todavía, solo placeholder).
 *
 * <p><b>Desenvolvimiento de caja (box UV unwrap):</b> para un cuboid con
 * tamaño {@code (x, y, z)} (ancho, alto, profundidad), las 6 caras se
 * desenvuelven en una cruz de {@code 2*(x+z)} de ancho por {@code (z+y)}
 * de alto -- fórmula verificada contra la implementación real de
 * Blockbench (modo "Box UV", `js/outliner/types/cube.js`/`js/uv/uv.js`
 * del repo `JannisX11/blockbench`, no inventada), el mismo layout que usa
 * el "Auto UV" clásico de Minecraft:
 * <pre>
 *        +---+---+
 *        |up |dwn|      fila superior: alto z
 *   +---+---+---+---+
 *   | w | n | e | s |   fila inferior: alto y
 *   +---+---+---+---+
 *     z   x   z   x
 * </pre>
 *
 * <p>Empaqueta cada footprint en la siguiente posición libre del atlas,
 * fila por fila (izquierda a derecha, de arriba hacia abajo), en el mismo
 * orden que trae la lista de cuboids de entrada -- determinista: la misma
 * lista de entrada siempre produce el mismo layout (AC de determinismo
 * del ticket 006). Si el conjunto no cabe en {@code textureWidth}x{@code textureHeight},
 * lanza {@link UvAtlasOverflowException} con las dimensiones mínimas que
 * sí lo harían caber -- el atlas nunca crece en silencio.
 */
public final class AlphaAutoPackStrategy implements UvLayoutStrategy {

	/** Único atlas de textura que existe este ciclo -- ver Face#texture(). */
	private static final int SINGLE_TEXTURE_INDEX = 0;

	private record Footprint(int width, int height) {
	}

	private record Placement(int x, int y) {
	}

	private record PackResult(List<Placement> placements, int totalHeight, int maxRowWidth) {
	}

	@Override
	public Result layout(List<Cuboid> cuboids, int textureWidth, int textureHeight) {
		List<Footprint> footprints = new ArrayList<>(cuboids.size());
		for (Cuboid cuboid : cuboids) {
			footprints.add(footprintOf(cuboid));
		}

		PackResult attempt = packWithinWidth(footprints, textureWidth);
		if (attempt.totalHeight() > textureHeight || attempt.maxRowWidth() > textureWidth) {
			int requiredWidth = Math.max(
					textureWidth, footprints.stream().mapToInt(Footprint::width).max().orElse(textureWidth));
			PackResult fallback = packWithinWidth(footprints, requiredWidth);
			int requiredHeight = Math.max(textureHeight, fallback.totalHeight());
			throw new UvAtlasOverflowException(textureWidth, textureHeight, requiredWidth, requiredHeight);
		}

		List<Cuboid> updatedCuboids = new ArrayList<>(cuboids.size());
		List<UvRegion> regions = new ArrayList<>();
		for (int i = 0; i < cuboids.size(); i++) {
			Cuboid cuboid = cuboids.get(i);
			Placement placement = attempt.placements().get(i);
			CuboidFaces faces = boxUnwrapFaces(cuboid, placement.x(), placement.y());

			updatedCuboids.add(
					new Cuboid(
							cuboid.id(), cuboid.name(), cuboid.boneId(), cuboid.from(), cuboid.to(), cuboid.origin(),
							cuboid.rotation(), faces));
			for (FaceName faceName : FaceName.values()) {
				regions.add(new UvRegion(cuboid.id(), faceName, faceOf(faces, faceName).uv()));
			}
		}

		return new Result(updatedCuboids, regions);
	}

	private static Footprint footprintOf(Cuboid cuboid) {
		int x = boxSizeAxis(cuboid.from().x(), cuboid.to().x());
		int y = boxSizeAxis(cuboid.from().y(), cuboid.to().y());
		int z = boxSizeAxis(cuboid.from().z(), cuboid.to().z());
		return new Footprint(2 * (x + z), z + y);
	}

	private static int boxSizeAxis(double from, double to) {
		return (int) Math.round(Math.abs(to - from));
	}

	/** Shelf-packing sin límite de alto -- envuelve de fila cuando se excede {@code width}. */
	private static PackResult packWithinWidth(List<Footprint> footprints, int width) {
		List<Placement> placements = new ArrayList<>(footprints.size());
		int cursorX = 0;
		int cursorY = 0;
		int rowHeight = 0;
		int maxRowWidth = 0;
		for (Footprint footprint : footprints) {
			if (cursorX > 0 && cursorX + footprint.width() > width) {
				maxRowWidth = Math.max(maxRowWidth, cursorX);
				cursorX = 0;
				cursorY += rowHeight;
				rowHeight = 0;
			}
			placements.add(new Placement(cursorX, cursorY));
			cursorX += footprint.width();
			rowHeight = Math.max(rowHeight, footprint.height());
		}
		maxRowWidth = Math.max(maxRowWidth, cursorX);
		return new PackResult(placements, cursorY + rowHeight, maxRowWidth);
	}

	private static CuboidFaces boxUnwrapFaces(Cuboid cuboid, int offsetX, int offsetY) {
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
		return new Face(new Vec4(u0, v0, u0 + width, v0 + height), SINGLE_TEXTURE_INDEX);
	}

	private static Face faceOf(CuboidFaces faces, FaceName name) {
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
