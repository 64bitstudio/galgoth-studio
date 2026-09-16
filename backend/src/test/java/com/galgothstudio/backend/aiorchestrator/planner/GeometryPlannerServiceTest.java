package com.galgothstudio.backend.aiorchestrator.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.provider.MockReasoningProvider;
import com.galgothstudio.backend.domain.jackson.Vec3JacksonModule;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.Proportions;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.TextureResolution;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.uv.AlphaAutoPackStrategy;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * `GeometryPlannerService` con `MockReasoningProvider` (ticket 025) --
 * nunca llama a la API real de Anthropic.
 */
class GeometryPlannerServiceTest {

	private static MobProjectModel emptyModel() {
		return new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, List.of(), List.of(),
				new TextureDocument(64, 64, null), new UvLayout(64, 64, List.of()), List.of(), new ExportSettings(FormatVersion.V5), List.of());
	}

	private static ModelIntent aModelIntent() {
		return new ModelIntent(
				"hunched humanoid", new Proportions(1.08, 1.18, 1.30, 1.10), 0.72, List.of("oversized hands"),
				List.of("desaturated grey-green skin"));
	}

	/** Sin `Vec3JacksonModule`/`Vec4JacksonModule` registrados, un `ObjectMapper` "a pelo" no sabe deserializar `[x,y,z]` como `Vec3` (mismo hallazgo ya documentado en `MobProjectModelValidatorTest`, ticket 004) -- en producción el `ObjectMapper` inyectado real (`JacksonConfig`, ticket 020) ya los trae. */
	private static ObjectMapper objectMapper() {
		return new ObjectMapper().registerModule(new Vec3JacksonModule()).registerModule(new Vec4JacksonModule());
	}

	private GeometryPlannerService newService(MockReasoningProvider mockProvider) {
		return new GeometryPlannerService(mockProvider, objectMapper(), new AlphaAutoPackStrategy());
	}

	@Test
	void una_propuesta_valida_se_aplica_via_GeometryEngine_y_su_UV_se_calcula_AC2() {
		MockReasoningProvider mockProvider = new MockReasoningProvider();
		mockProvider.setNextResponse(
				"""
				[
				  {"op":"createBone","tempId":"root","name":"body","parentId":null,"pivot":[0,0,0],"rotation":[0,0,0]},
				  {"op":"createCuboid","tempId":"c1","name":"body","boneId":"root","from":[-4,0,-4],"to":[4,8,4],"origin":[0,4,0],"rotation":[0,0,0]}
				]
				""");
		GeometryPlannerService service = newService(mockProvider);

		GeometryPlanResult result = service.plan(aModelIntent(), emptyModel());

		assertThat(result.model().bones()).hasSize(1);
		assertThat(result.model().cuboids()).hasSize(1);
		assertThat(result.model().cuboids().get(0).faces().north().texture()).isZero(); // AutoUv corrió de verdad
		assertThat(result.providerResponse().provider()).isEqualTo("mock");
	}

	/**
	 * Ticket 033 -- el default literal de `MockReasoningProvider` SIN
	 * ningún `setNextResponse` explícito (el caso real de un servidor
	 * recién levantado con `AI_REASONING_PROVIDER=mock`, como en la
	 * suite Playwright de aceptación) debe ser un rig de creación
	 * aplicable sobre un modelo vacío -- nunca el ejemplo de edición
	 * (que fallaría acá, sus targets no existen todavía).
	 */
	@Test
	void sinConfigurarNadaElDefaultDelMockEsUnRigDeCreacionAplicableSobreUnModeloVacio() {
		GeometryPlannerService service = newService(new MockReasoningProvider());

		GeometryPlanResult result = service.plan(aModelIntent(), emptyModel());

		assertThat(result.model().bones()).isNotEmpty();
		assertThat(result.model().cuboids()).isNotEmpty();
	}

	/**
	 * Ticket 042, Diseño técnico §7: el atlas inicial de un mob generado
	 * por IA es SIEMPRE la potencia de 2 que contiene el footprint
	 * empaquetado de sus cuboids reales -- nunca el
	 * {@code TextureDocument} de {@code startingModel} (acá 64x64,
	 * deliberadamente distinto del footprint, para probar que se IGNORA) ni
	 * un valor fijo hardcodeado (128x128, el que existía antes de 042).
	 *
	 * <p><b>CAMBIO DE COMPORTAMIENTO REAL, ticket 103</b>: las cifras
	 * esperadas de este test cambiaron de 32x16 a 64x32. No es un ajuste
	 * cosmético del test: hasta 103 la densidad estaba hardcodeada en
	 * {@code TexelDensity.X1}; ahora sale del tope elegido en Configuración
	 * ({@link TextureResolution}, default {@code MAX_128}), y para este
	 * cuboid la densidad X2 entra de sobra en ese tope -- así que el
	 * pipeline elige X2 y el atlas resultante es el doble por lado. El
	 * footprint sigue siendo el que manda; lo que cambió es a qué densidad
	 * se mide. Reportado explícitamente en el PR del ticket 103 por ser un
	 * cambio de comportamiento del flujo por defecto, no solo del selector.
	 */
	@Test
	void elAtlasInicial_seCalculaDelFootprintEmpaquetadoRealDeLosCuboids_nuncaDeUnValorFijo() {
		MockReasoningProvider mockProvider = new MockReasoningProvider();
		mockProvider.setNextResponse(
				"""
				[
				  {"op":"createBone","tempId":"root","name":"body","parentId":null,"pivot":[0,0,0],"rotation":[0,0,0]},
				  {"op":"createCuboid","tempId":"c1","name":"body","boneId":"root","from":[-4,0,-4],"to":[4,8,4],"origin":[0,4,0],"rotation":[0,0,0]}
				]
				""");
		GeometryPlannerService service = newService(mockProvider);

		GeometryPlanResult result = service.plan(aModelIntent(), emptyModel());

		// Cuboid 8(x) x 8(y) x 4(z) -- footprint a X1 = 2*(8+4)=24 de ancho,
		// (4+8)=12 de alto (atlas 32x16); a X2, el doble por lado -> 48x24,
		// atlas = potencia de 2 inmediatamente contenedora: 64x32. Con el
		// tope default (128) la densidad X2 entra, así que es la elegida.
		assertThat(result.model().texture().width()).isEqualTo(64);
		assertThat(result.model().texture().height()).isEqualTo(32);
		assertThat(result.model().uv().textureWidth()).isEqualTo(64);
		assertThat(result.model().uv().textureHeight()).isEqualTo(32);
	}

	/**
	 * Ticket 103 (HU-10) -- la resolución elegida en Configuración cambia de
	 * verdad el atlas resultante, reemplazando el hardcode
	 * {@code TexelDensity.X1}: el MISMO cuboid, con dos topes distintos, da
	 * dos atlas reales distintos. El tope acota la densidad; el atlas sigue
	 * saliendo del packing (§7), nunca se infla para "llenar" el tope.
	 */
	@Test
	void laResolucionElegidaCambiaElAtlasReal_yaNoEsElHardcodeX1_AC() {
		MockReasoningProvider mockProvider = new MockReasoningProvider();
		// Cuboid 16(x) x 16(y) x 8(z): a X1 el footprint empaquetado da un
		// atlas de 64x32; a X2 (footprint lineal x2) da 128x64.
		String rig =
				"""
				[
				  {"op":"createBone","tempId":"root","name":"body","parentId":null,"pivot":[0,0,0],"rotation":[0,0,0]},
				  {"op":"createCuboid","tempId":"c1","name":"body","boneId":"root","from":[-8,0,-4],"to":[8,16,4],"origin":[0,8,0],"rotation":[0,0,0]}
				]
				""";
		mockProvider.setNextResponse(rig);
		GeometryPlannerService service = newService(mockProvider);
		GeometryPlanResult planned = service.plan(aModelIntent(), emptyModel());

		MobProjectModel conTope64 = service.applyOperations(
				planned.operations(), planned.providerResponse(), emptyModel(), TextureResolution.MAX_64);
		MobProjectModel conTope128 = service.applyOperations(
				planned.operations(), planned.providerResponse(), emptyModel(), TextureResolution.MAX_128);

		// Con tope 64, la densidad alta (atlas 128) no entra -> se usa X1.
		assertThat(conTope64.texture().width()).isEqualTo(64);
		assertThat(conTope64.texture().height()).isEqualTo(32);
		// Con tope 128 sí entra la densidad alta -> atlas real más grande.
		assertThat(conTope128.texture().width()).isEqualTo(128);
		assertThat(conTope128.texture().height()).isEqualTo(64);
		assertThat(conTope128.uv().textureWidth()).isEqualTo(128);
	}

	@Test
	void una_operacion_fuera_de_la_whitelist_detiene_el_flujo_AC2() {
		MockReasoningProvider mockProvider = new MockReasoningProvider();
		mockProvider.setNextResponse("""
				[{"op":"deleteEverything","target":"x"}]
				""");
		GeometryPlannerService service = newService(mockProvider);
		ModelIntent modelIntent = aModelIntent();
		MobProjectModel startingModel = emptyModel();

		assertThatThrownBy(() -> service.plan(modelIntent, startingModel)).isInstanceOf(InvalidGeometryProposalException.class);
	}

	@Test
	void un_JSON_malformado_se_reporta_como_InvalidGeometryProposalException_conservando_el_providerResponse() {
		MockReasoningProvider mockProvider = new MockReasoningProvider();
		mockProvider.setNextResponse("esto no es un array JSON");
		GeometryPlannerService service = newService(mockProvider);
		ModelIntent modelIntent = aModelIntent();
		MobProjectModel startingModel = emptyModel();

		assertThatThrownBy(() -> service.plan(modelIntent, startingModel))
				.isInstanceOf(InvalidGeometryProposalException.class)
				.satisfies(e -> assertThat(((InvalidGeometryProposalException) e).providerResponse().provider()).isEqualTo("mock"));
	}

	@Test
	void una_geometria_con_dimensiones_invalidas_es_rechazada_por_el_GeometryEngine_AC2() {
		MockReasoningProvider mockProvider = new MockReasoningProvider();
		mockProvider.setNextResponse(
				"""
				[
				  {"op":"createBone","tempId":"root","name":"body","parentId":null,"pivot":[0,0,0],"rotation":[0,0,0]},
				  {"op":"createCuboid","tempId":"c1","name":"body","boneId":"root","from":[4,0,-4],"to":[-4,8,4],"origin":[0,4,0],"rotation":[0,0,0]}
				]
				""");
		GeometryPlannerService service = newService(mockProvider);
		ModelIntent modelIntent = aModelIntent();
		MobProjectModel startingModel = emptyModel();

		assertThatThrownBy(() -> service.plan(modelIntent, startingModel)).isInstanceOf(InvalidGeometryProposalException.class);
	}

}
