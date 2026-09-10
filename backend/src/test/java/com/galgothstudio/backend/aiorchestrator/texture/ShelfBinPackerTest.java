package com.galgothstudio.backend.aiorchestrator.texture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Bin-packing determinista tipo shelf -- ticket 053, Diseño técnico
 * §11/§21 (aislamiento espacial: no-solape + fallback/batching
 * explícito).
 */
class ShelfBinPackerTest {

	@Test
	void itemsQueEntranEnUnaFila_seUbicanUnoAlLadoDelOtroConGutter() {
		List<ShelfBinPacker.Item> items =
				List.of(new ShelfBinPacker.Item("a", 100, 50), new ShelfBinPacker.Item("b", 100, 50));

		List<ShelfBinPacker.Bin> bins = ShelfBinPacker.pack(items, 1000, 2);

		assertThat(bins).hasSize(1);
		ShelfBinPacker.Bin bin = bins.getFirst();
		assertThat(bin.items()).containsExactly(
				new ShelfBinPacker.PlacedItem("a", 0, 0, 100, 50), new ShelfBinPacker.PlacedItem("b", 102, 0, 202, 50));
		assertThat(bin.width()).isEqualTo(202);
		assertThat(bin.height()).isEqualTo(50);
	}

	@Test
	void unItemQueNoEntraEnLaFilaActual_abreUnaFilaNuevaEnElMismoBin() {
		List<ShelfBinPacker.Item> items =
				List.of(new ShelfBinPacker.Item("a", 90, 40), new ShelfBinPacker.Item("b", 90, 40));

		List<ShelfBinPacker.Bin> bins = ShelfBinPacker.pack(items, 100, 2);

		assertThat(bins).hasSize(1);
		ShelfBinPacker.Bin bin = bins.getFirst();
		assertThat(bin.items()).containsExactly(
				new ShelfBinPacker.PlacedItem("a", 0, 0, 90, 40), new ShelfBinPacker.PlacedItem("b", 0, 42, 90, 82));
		assertThat(bin.width()).isEqualTo(90);
		assertThat(bin.height()).isEqualTo(82);
	}

	@Test
	void unItemQueNoEntraNiEnFilaNiEnBinActual_abreUnBinNuevo() {
		// maxDimension=100: "a" 90x90 llena el bin casi entero (no cabe otra
		// fila de 90 de alto sin pasarse), "b" 90x90 no entra ni en la misma
		// fila (90+2+90 > 100) ni en una fila nueva (90+2+90 > 100) -> bin nuevo.
		List<ShelfBinPacker.Item> items =
				List.of(new ShelfBinPacker.Item("a", 90, 90), new ShelfBinPacker.Item("b", 90, 90));

		List<ShelfBinPacker.Bin> bins = ShelfBinPacker.pack(items, 100, 2);

		assertThat(bins).hasSize(2);
		assertThat(bins.get(0).items()).containsExactly(new ShelfBinPacker.PlacedItem("a", 0, 0, 90, 90));
		assertThat(bins.get(1).items()).containsExactly(new ShelfBinPacker.PlacedItem("b", 0, 0, 90, 90));
	}

	@Test
	void ningunItemDeUnBinSeSolapaConOtro_paraUnCasoConVariasFilasYColumnas() {
		List<ShelfBinPacker.Item> items = List.of(
				new ShelfBinPacker.Item("a", 40, 30), new ShelfBinPacker.Item("b", 40, 20),
				new ShelfBinPacker.Item("c", 40, 40), new ShelfBinPacker.Item("d", 30, 10));

		List<ShelfBinPacker.Bin> bins = ShelfBinPacker.pack(items, 90, 2);

		assertThat(bins).hasSize(1);
		List<ShelfBinPacker.PlacedItem> placed = bins.getFirst().items();
		for (int i = 0; i < placed.size(); i++) {
			for (int j = i + 1; j < placed.size(); j++) {
				assertThat(overlap(placed.get(i), placed.get(j)))
						.as("items %s y %s no deben solaparse", placed.get(i).id(), placed.get(j).id())
						.isFalse();
			}
		}
	}

	@Test
	void unItemQueExcedePorSiSoloElLimiteTecnico_lanzaExcepcionExplicita() {
		List<ShelfBinPacker.Item> items = List.of(new ShelfBinPacker.Item("gigante", 2000, 10));

		assertThatThrownBy(() -> ShelfBinPacker.pack(items, 1536, 2)).isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("gigante");
	}

	@Test
	void packConLaMismaEntrada_produceSiempreElMismoResultado_determinista() {
		List<ShelfBinPacker.Item> items = List.of(
				new ShelfBinPacker.Item("a", 50, 50), new ShelfBinPacker.Item("b", 60, 60),
				new ShelfBinPacker.Item("c", 70, 70));

		List<ShelfBinPacker.Bin> first = ShelfBinPacker.pack(items, 120, 3);
		List<ShelfBinPacker.Bin> second = ShelfBinPacker.pack(items, 120, 3);

		assertThat(first).isEqualTo(second);
	}

	private static boolean overlap(ShelfBinPacker.PlacedItem a, ShelfBinPacker.PlacedItem b) {
		return a.x0() < b.x1() && b.x0() < a.x1() && a.y0() < b.y1() && b.y0() < a.y1();
	}

}
