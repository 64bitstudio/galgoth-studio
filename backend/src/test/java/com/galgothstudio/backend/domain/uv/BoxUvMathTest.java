package com.galgothstudio.backend.domain.uv;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.Vec3;
import org.junit.jupiter.api.Test;

/**
 * Ticket 041, AC de paridad: {@code BoxUvMath} es la extracción literal de
 * la matemática de box-unwrap/footprint de {@code AlphaAutoPackStrategy}
 * (ticket 006/007) -- estos valores son EXACTAMENTE los mismos ya
 * verificados en {@link AlphaAutoPackStrategyTest} (cubo 8x8x8, offset
 * (0,0)), sin tocar ese archivo.
 */
class BoxUvMathTest {

	private static Cuboid cube(String id, double size) {
		double half = size / 2;
		Face placeholder = new Face(null, null);
		CuboidFaces emptyFaces = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
		return new Cuboid(
				id, "cube-" + id, "bone-1", new Vec3(-half, -half, -half), new Vec3(half, half, half), new Vec3(0, 0, 0),
				new Vec3(0, 0, 0), emptyFaces);
	}

	@Test
	void footprintOfUnCuboCubico_calculaElFootprintDeCruzEstandar() {
		// Cubo 8x8x8 -- footprint = 2*(8+8)=32 de ancho, (8+8)=16 de alto,
		// mismo valor documentado en AlphaAutoPackStrategyTest.
		BoxUvMath.Footprint footprint = BoxUvMath.footprintOf(cube("head", 8));

		assertThat(footprint).isEqualTo(new BoxUvMath.Footprint(32, 16));
	}

	// -- Ticket 042, Diseño técnico §7: footprintOf/boxUnwrapFaces(cuboid, TexelDensity) --

	@Test
	void footprintOfSinDensidad_delegaEnDensidadX1_sinCambiarComportamientoDe041() {
		Cuboid head = cube("head", 8);

		assertThat(BoxUvMath.footprintOf(head)).isEqualTo(BoxUvMath.footprintOf(head, TexelDensity.X1));
	}

	@Test
	void boxUnwrapFacesSinDensidad_delegaEnDensidadX1_sinCambiarComportamientoDe041() {
		Cuboid head = cube("head", 8);

		assertThat(BoxUvMath.boxUnwrapFaces(head, 3, 5)).isEqualTo(BoxUvMath.boxUnwrapFaces(head, 3, 5, TexelDensity.X1));
	}

	@Test
	void unaCaraFisicaDe8x8Unidades_aDensidadX1_produce8x8Texels() {
		// AC del ticket 042: cuboid con una cara física de 8x8 unidades, a
		// densidad x1, produce una región de 8x8 texels.
		Cuboid head = cube("head", 8);

		CuboidFaces faces = BoxUvMath.boxUnwrapFaces(head, 0, 0, TexelDensity.X1);
		Face north = BoxUvMath.faceOf(faces, FaceName.NORTH);

		assertThat(north.uv().c() - north.uv().a()).isEqualTo(8);
		assertThat(north.uv().d() - north.uv().b()).isEqualTo(8);
	}

	@Test
	void laMismaCaraFisicaDe8x8Unidades_aDensidadX2_produce16x16Texels() {
		// Mismo AC, a densidad x2: 16x16 texels -- "el mismo cuboid,
		// empaquetado a densidades distintas, produce footprints de tamaño
		// distinto" (nunca "el mismo layout UV al doble de resolución").
		Cuboid head = cube("head", 8);

		CuboidFaces faces = BoxUvMath.boxUnwrapFaces(head, 0, 0, TexelDensity.X2);
		Face north = BoxUvMath.faceOf(faces, FaceName.NORTH);

		assertThat(north.uv().c() - north.uv().a()).isEqualTo(16);
		assertThat(north.uv().d() - north.uv().b()).isEqualTo(16);
	}

	@Test
	void footprintOfADensidadX2_esElDobleLinealDelFootprintADensidadX1() {
		Cuboid head = cube("head", 8); // footprint X1 = 32x16 (ver test de arriba)

		BoxUvMath.Footprint footprintX2 = BoxUvMath.footprintOf(head, TexelDensity.X2);

		assertThat(footprintX2).isEqualTo(new BoxUvMath.Footprint(64, 32));
	}

	@Test
	void boxUnwrapFacesEnOffsetCero_reproduceLosValoresYaVerificadosDeAlphaAutoPackStrategy() {
		CuboidFaces faces = BoxUvMath.boxUnwrapFaces(cube("head", 8), 0, 0);

		assertUv(faces.up(), 8, 0, 16, 8);
		assertUv(faces.down(), 16, 0, 24, 8);
		assertUv(faces.west(), 0, 8, 8, 16);
		assertUv(faces.north(), 8, 8, 16, 16);
		assertUv(faces.east(), 16, 8, 24, 16);
		assertUv(faces.south(), 24, 8, 32, 16);
		assertThat(faces.north().texture()).isZero();
	}

	@Test
	void boxUnwrapFacesConOffsetNoNulo_desplazaLas6CarasPorIgual() {
		CuboidFaces faces = BoxUvMath.boxUnwrapFaces(cube("head", 8), 10, 10);

		assertUv(faces.up(), 18, 10, 26, 18);
		assertUv(faces.west(), 10, 18, 18, 26);
	}

	// -- Ticket 118: la densidad se aplica ANTES de redondear --------------

	/** Cuboid con tamaños distintos (y posiblemente fraccionarios) por eje, anclado en el origen. */
	private static Cuboid box(String id, double sx, double sy, double sz) {
		Face placeholder = new Face(null, null);
		CuboidFaces emptyFaces = new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
		return new Cuboid(
				id, "box-" + id, "bone-1", new Vec3(0, 0, 0), new Vec3(sx, sy, sz), new Vec3(0, 0, 0), new Vec3(0, 0, 0),
				emptyFaces);
	}

	/**
	 * AC del 118. Antes: {@code round(0,4) * 4 = 0} -- el eje colapsaba a
	 * CERO y la cara salía degenerada a cualquier densidad, que es lo
	 * contrario de lo que promete subir la densidad.
	 */
	@Test
	void unEjeSubUnitarioYaNoColapsaACero_seEscalaAntesDeRedondear_AC() {
		BoxUvMath.Footprint footprint = BoxUvMath.footprintOf(box("ojo", 0.4, 0.4, 0.4), TexelDensity.X4);

		// round(0,4 * 4) = 2 por eje -> ancho 2*(2+2)=8, alto 2+2=4.
		assertThat(footprint).isEqualTo(new BoxUvMath.Footprint(8, 4));
	}

	/** AC del 118: un eje fraccionario deja de perder resolución -- round(1,4*4)=6, no round(1,4)*4=4. */
	@Test
	void unEjeFraccionarioUsaLaResolucionQueLeCorresponde_AC() {
		CuboidFaces faces = BoxUvMath.boxUnwrapFaces(box("grieta", 1.4, 1.4, 1.4), 0, 0, TexelDensity.X4);

		// x = y = z = round(1,4 * 4) = 6. north empieza en (z, z) y mide x por y.
		assertUv(faces.north(), 6, 6, 12, 12);
	}

	/**
	 * AC del 118, el invariante que no se puede romper: lo que el packer
	 * RESERVA (`footprintOf`) y lo que se COLOCA (`boxUnwrapFaces`) tienen
	 * que seguir coincidiendo exactamente, porque las dos rutas hacen el
	 * mismo cálculo por separado.
	 */
	@Test
	void footprintYCarasColocadasSiguenCoincidiendo_enTodaDensidadYConEjesFraccionarios_AC() {
		for (TexelDensity density : TexelDensity.values()) {
			for (Cuboid cuboid : new Cuboid[] {box("a", 0.4, 7.6, 2.5), box("b", 8, 8, 8), box("c", 1.4, 0.6, 3.2)}) {
				BoxUvMath.Footprint footprint = BoxUvMath.footprintOf(cuboid, density);
				CuboidFaces faces = BoxUvMath.boxUnwrapFaces(cuboid, 0, 0, density);

				double maxU = Math.max(faces.south().uv().c(), faces.down().uv().c());
				double maxV = Math.max(faces.south().uv().d(), faces.west().uv().d());
				assertThat(maxU).as("ancho reservado vs colocado, densidad %s", density).isEqualTo((double) footprint.width());
				assertThat(maxV).as("alto reservado vs colocado, densidad %s", density).isEqualTo((double) footprint.height());
			}
		}
	}

	/**
	 * AC del 118: a X1 nada cambia (`round(v * 1)` es `round(v)`), así que
	 * las fixtures de los tickets 006/007 siguen valiendo sin tocarlas.
	 */
	@Test
	void aDensidadX1ElComportamientoEsIdenticoAlAnterior_AC() {
		Cuboid raro = box("raro", 1.4, 0.6, 7.5);

		BoxUvMath.Footprint footprint = BoxUvMath.footprintOf(raro, TexelDensity.X1);

		// round(1,4)=1, round(0,6)=1, round(7,5)=8 -> ancho 2*(1+8)=18, alto 8+1=9.
		assertThat(footprint).isEqualTo(new BoxUvMath.Footprint(18, 9));
	}

	/**
	 * Decisión tomada en el 118 y explícita, no un efecto colateral: un eje
	 * que EXISTE pero es tan chico que aun escalado redondea a cero recibe
	 * 1 texel, nunca cero -- una cara degenerada no se puede pintar ni
	 * mostrar. Un eje de tamaño REAL cero (cuboid plano de verdad) sigue
	 * dando cero: ahí no hay nada que inventar, y el reporte de calidad lo
	 * cuenta como degenerado.
	 */
	@Test
	void unEjeMinusculoPeroRealRecibeUnTexel_unoDeTamanoCeroSigueEnCero() {
		assertThat(BoxUvMath.footprintOf(box("pelusa", 0.05, 0.05, 0.05), TexelDensity.X1))
				.isEqualTo(new BoxUvMath.Footprint(4, 2));
		assertThat(BoxUvMath.footprintOf(box("plano", 0, 8, 8), TexelDensity.X1))
				.isEqualTo(new BoxUvMath.Footprint(16, 16));
	}

	private static void assertUv(Face face, double u0, double v0, double u1, double v1) {
		assertThat(face.uv().a()).isEqualTo(u0);
		assertThat(face.uv().b()).isEqualTo(v0);
		assertThat(face.uv().c()).isEqualTo(u1);
		assertThat(face.uv().d()).isEqualTo(v1);
	}

}
