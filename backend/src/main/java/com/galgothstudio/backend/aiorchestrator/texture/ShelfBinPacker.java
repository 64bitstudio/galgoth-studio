package com.galgothstudio.backend.aiorchestrator.texture;

import java.util.ArrayList;
import java.util.List;

/**
 * Bin-packing determinista tipo "shelf" (fila por fila, izquierda a
 * derecha / arriba a abajo, en el orden de llegada de los items --
 * NUNCA reordena para "optimizar" el packing) usado por
 * {@link TextureGenerationSheetPlanner} para:
 * <ul>
 * <li>el layout INTERNO de una {@link TextureGenerationSheet} (los
 * {@code sheetRect} de sus {@link CuboidFacePlacement}, nunca solapados,
 * separados por un gutter explícito);</li>
 * <li>el fallback/batching en N sub-sheets cuando un bone excede
 * {@link TextureGenerationSheetPlanner#MAX_SHEET_DIMENSION_PX} (Diseño
 * técnico §11/§21) -- cada {@link Bin} devuelto es una sub-sheet.</li>
 * </ul>
 *
 * <p>Package-private: es un detalle de implementación del planner, no
 * un contrato público del ticket 053.
 */
final class ShelfBinPacker {

	private ShelfBinPacker() {
	}

	record Item(String id, int width, int height) {
	}

	record PlacedItem(String id, int x0, int y0, int x1, int y1) {
	}

	record Bin(List<PlacedItem> items, int width, int height) {
	}

	/**
	 * Empaqueta {@code items} en uno o más {@link Bin} de a lo sumo
	 * {@code maxDimensionPx} de lado, separando items adyacentes por
	 * {@code gutterPx}. Un solo {@code Item} cuyo ancho o alto por sí solo
	 * exceda {@code maxDimensionPx} no se puede sub-dividir más --
	 * {@link IllegalStateException} explícita en vez de un resultado
	 * silenciosamente incorrecto.
	 */
	static List<Bin> pack(List<Item> items, int maxDimensionPx, int gutterPx) {
		List<Bin> bins = new ArrayList<>();
		BinBuilder current = new BinBuilder();
		for (Item item : items) {
			requireFitsAlone(item, maxDimensionPx);
			if (!current.tryPlaceInCurrentRow(item, maxDimensionPx, gutterPx)
					&& !current.tryPlaceInNewRow(item, maxDimensionPx, gutterPx)) {
				bins.add(current.build());
				current = new BinBuilder();
				current.placeInEmptyBin(item);
			}
		}
		bins.add(current.build());
		return bins;
	}

	private static void requireFitsAlone(Item item, int maxDimensionPx) {
		if (item.width() > maxDimensionPx || item.height() > maxDimensionPx) {
			throw new IllegalStateException(
					"El item '" + item.id() + "' (" + item.width() + "x" + item.height()
							+ ") excede por sí solo el límite técnico de sheet (" + maxDimensionPx
							+ "px) -- no se puede dividir más.");
		}
	}

	/** Estado mutable de UN bin en construcción -- fila (shelf) actual + acumuladores de tamaño. */
	private static final class BinBuilder {

		private final List<PlacedItem> placed = new ArrayList<>();
		private int rowY;
		private int rowHeight;
		private int cursorX;
		private boolean rowEmpty = true;
		private int maxX;
		private int maxY;

		/** Intenta agregar {@code item} a la fila actual (misma altura de fila, a la derecha del último). */
		boolean tryPlaceInCurrentRow(Item item, int maxDimensionPx, int gutterPx) {
			int x0 = rowEmpty ? 0 : cursorX + gutterPx;
			int x1 = x0 + item.width();
			if (x1 > maxDimensionPx) {
				return false;
			}
			place(item, x0, rowY);
			cursorX = x1;
			rowHeight = Math.max(rowHeight, item.height());
			rowEmpty = false;
			return true;
		}

		/**
		 * Intenta abrir una fila nueva DENTRO del mismo bin. Invariante: solo
		 * se llama cuando {@link #tryPlaceInCurrentRow} ya devolvió
		 * {@code false}, lo que implica {@code rowEmpty == false} (el primer
		 * item de una fila vacía SIEMPRE entra en ella, ver
		 * {@link #requireFitsAlone}) -- nunca hace falta un branch para fila
		 * vacía acá.
		 */
		boolean tryPlaceInNewRow(Item item, int maxDimensionPx, int gutterPx) {
			int newRowY = rowY + rowHeight + gutterPx;
			if (newRowY + item.height() > maxDimensionPx) {
				return false;
			}
			rowY = newRowY;
			rowHeight = 0;
			cursorX = 0;
			rowEmpty = true;
			place(item, 0, rowY);
			cursorX = item.width();
			rowHeight = item.height();
			rowEmpty = false;
			return true;
		}

		void placeInEmptyBin(Item item) {
			place(item, 0, 0);
			cursorX = item.width();
			rowHeight = item.height();
			rowEmpty = false;
		}

		private void place(Item item, int x0, int y0) {
			int x1 = x0 + item.width();
			int y1 = y0 + item.height();
			placed.add(new PlacedItem(item.id(), x0, y0, x1, y1));
			maxX = Math.max(maxX, x1);
			maxY = Math.max(maxY, y1);
		}

		Bin build() {
			return new Bin(placed, maxX, maxY);
		}
	}
}
