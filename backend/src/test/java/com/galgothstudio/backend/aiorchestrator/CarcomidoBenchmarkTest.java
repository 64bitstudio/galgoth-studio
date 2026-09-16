package com.galgothstudio.backend.aiorchestrator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.TestcontainersConfiguration;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEntity;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.provider.MockReasoningProvider;
import com.galgothstudio.backend.aiorchestrator.provider.MockVisionProvider;
import com.galgothstudio.backend.aiorchestrator.provider.ReasoningRequest;
import com.galgothstudio.backend.aiorchestrator.provider.StructuredReasoningProvider;
import com.galgothstudio.backend.aiorchestrator.provider.VisionModelProvider;
import com.galgothstudio.backend.asset.AssetStorageService;
import com.galgothstudio.backend.domain.export.BBModelExporterV5;
import com.galgothstudio.backend.domain.export.validation.FmmCompatibilityValidator;
import com.galgothstudio.backend.domain.export.validation.ValidationResult;
import com.galgothstudio.backend.domain.model.ModelGenerationQualityReport;
import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.SemanticPartCategory;
import java.io.File;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/**
 * Benchmark de aceptación de la epic "anatomía y textura por capas"
 * (ticket 105, HU-5 de `docs/definiciones/anatomia-por-capas-generacion-mobs.md`):
 * corre el pipeline de generación COMPLETO contra el
 * {@code ModelIntent} del personaje de referencia real
 * (`galgoth_studio_build_pack/references/carcomido_reference.png`, ya
 * versionado en el repo) y mide el resultado con el
 * {@link ModelGenerationQualityReport} del ticket 104 -- no "a ojo".
 *
 * <p><b>Qué prueba REALMENTE este test, y qué NO</b> (distinción
 * importante, declarada acá para que un verde no se lea como más de lo
 * que es). La suite automatizada corre SIEMPRE con proveedores mock
 * ({@code ai.*-provider=mock}) -- nunca llama a la IA real. Por lo tanto
 * este test prueba la MECÁNICA del pipeline de punta a punta:
 * <ul>
 *   <li>que las features detectadas en la referencia se propagan como
 *       categorías cerradas hasta la geometría generada;</li>
 *   <li>que la cobertura se mide por igualdad de enum y reporta
 *       explícitamente lo que falta;</li>
 *   <li>que el modelo resultante exporta a `.bbmodel` y pasa la
 *       validación FMM real;</li>
 *   <li>que ningún componente tiene una rama especial para "Carcomido".</li>
 * </ul>
 * Lo que NO prueba: que un modelo generado por la IA REAL se PAREZCA a
 * la imagen de referencia. Eso depende de la calidad del proveedor, no
 * del pipeline, y solo puede verificarse con una corrida real contra
 * `studio-dev` (misma verificación en vivo pendiente del ticket 102).
 * Declarado también en la sección "Hecho" del ticket 105.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@TestPropertySource(properties = {"ai.vision-provider=mock", "ai.reasoning-provider=mock"})
class CarcomidoBenchmarkTest {

	private static final File CARCOMIDO_INTENT = new File("../contracts/fixtures/carcomido-model-intent.json");

	/** Formato real de cada bone primario en el prompt de `SecondaryGeometryPlanner`: "- <id> (<name>), pivot=[x,y,z]". */
	private static final Pattern PRIMARY_BONE = Pattern.compile("- (\\S+) \\(([^)]*)\\), pivot=\\[([-\\d.]+),([-\\d.]+),([-\\d.]+)]");

	private static final String SECONDARY_PROMPT_VERSION = "secondary-planner-v1";

	/**
	 * Rasgos del Carcomido que la geometría secundaria debe materializar
	 * -- las categorías vienen del fixture del personaje, no de una lista
	 * inventada acá.
	 */
	private static final List<SemanticPartCategory> RASGOS_CARACTERISTICOS = List.of(
			SemanticPartCategory.CLAW, SemanticPartCategory.TORN_CLOTH, SemanticPartCategory.JAW,
			SemanticPartCategory.EMISSIVE_CRACK, SemanticPartCategory.LOINCLOTH, SemanticPartCategory.EYE);

	private static final byte[] TINY_PNG =
			Base64Png.BYTES;

	/** PNG 1x1 real -- mismo fixture que el resto de los tests de generación. */
	private static final class Base64Png {
		static final byte[] BYTES = java.util.Base64.getDecoder()
				.decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

		private Base64Png() {
		}
	}

	@Autowired
	private MobGenerationService mobGenerationService;

	@Autowired
	private VisionModelProvider visionModelProvider;

	@Autowired
	private StructuredReasoningProvider reasoningProvider;

	@Autowired
	private AssetStorageService assetStorageService;

	@Autowired
	private AiJobRepository aiJobRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ObjectMapper objectMapper;

	/** Reset obligatorio de ambos dobles -- contexto de Spring cacheado y compartido entre clases de test (hallazgo del ticket 099). */
	@BeforeEach
	void setUpCarcomidoFixture() throws Exception {
		MockVisionProvider mockVision = (MockVisionProvider) visionModelProvider;
		mockVision.setNextResponse(Files.readString(CARCOMIDO_INTENT.toPath()));
		mockVision.setOnCall(() -> {
		});
		MockReasoningProvider mockReasoning = (MockReasoningProvider) reasoningProvider;
		mockReasoning.reset();
		mockReasoning.setNextResponseFactory(CarcomidoBenchmarkTest::secondaryGeometryForCarcomido);
	}

	/**
	 * Geometría secundaria "como la devolvería un proveedor que entendió la
	 * referencia": un cuboid por rasgo característico, colgado de bones
	 * primarios REALES (ids UUID que solo existen en runtime, extraídos del
	 * propio prompt) y colocado sobre el pivote de su bone para no ser
	 * rechazado por `SecondaryGeometryConstraints`.
	 */
	private static String secondaryGeometryForCarcomido(ReasoningRequest request) {
		if (!SECONDARY_PROMPT_VERSION.equals(request.promptVersion())) {
			return null; // cualquier otro paso sigue con el default del mock
		}
		Matcher matcher = PRIMARY_BONE.matcher(request.userPrompt());
		List<String> ops = new ArrayList<>();
		int index = 0;
		while (matcher.find() && index < RASGOS_CARACTERISTICOS.size()) {
			String boneId = matcher.group(1);
			double px = Double.parseDouble(matcher.group(3));
			double py = Double.parseDouble(matcher.group(4));
			double pz = Double.parseDouble(matcher.group(5));
			ops.add(
					("{\"op\":\"createCuboid\",\"tempId\":\"carc_%d\",\"name\":\"rasgo_%d\",\"boneId\":\"%s\","
							+ "\"from\":[%s,%s,%s],\"to\":[%s,%s,%s],\"origin\":[%s,%s,%s],\"rotation\":[0,0,0],\"semanticPart\":\"%s\"}")
							.formatted(
									index, index, boneId, px, py, pz, px + 1, py + 1, pz + 1, px, py, pz,
									RASGOS_CARACTERISTICOS.get(index).name()));
			index++;
		}
		return "[" + String.join(",", ops) + "]";
	}

	private UUID aMobWithCarcomidoReference() {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", projectId, "Galgoth");
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				mobId, projectId, "Carcomido_benchmark", "humanoid", "draft");
		String storageKey = "mobs/" + mobId + "/references/carcomido.png";
		assetStorageService.put(storageKey, TINY_PNG, "image/png");
		jdbc.update(
				"insert into reference_images (id, mob_id, storage_key, width, height, content_type) values (?, ?, ?, ?, ?, ?)",
				UUID.randomUUID(), mobId, storageKey, 1, 1, "image/png");
		return mobId;
	}

	private AiJobEntity awaitTerminalStatus(UUID jobId) {
		Awaitility.await()
				.atMost(Duration.ofSeconds(15))
				.pollInterval(Duration.ofMillis(25))
				.until(() -> !"running".equals(aiJobRepository.findById(jobId).orElseThrow().getStatus()));
		return aiJobRepository.findById(jobId).orElseThrow();
	}

	private ModelIntent carcomidoIntent() throws Exception {
		return objectMapper.readValue(CARCOMIDO_INTENT, ModelIntent.class);
	}

	@Test
	void elPipelineCompletoCorreDePuntaAPuntaContraElBenchmark_yElReporteDeCalidadQuedaComoEvidencia_AC() throws Exception {
		UUID mobId = aMobWithCarcomidoReference();

		UUID jobId = mobGenerationService.startGeneration(mobId);
		AiJobEntity job = awaitTerminalStatus(jobId);

		assertThat(job.getStatus()).isEqualTo("completed");
		MobProjectModel model = objectMapper.readValue(job.getProposalJson(), MobProjectModel.class);

		ModelGenerationQualityReport report =
				ModelGenerationQualityReport.of(carcomidoIntent(), model, ModelGenerationQualityReport.Metric.of(1));

		// Evidencia legible en la salida del test -- el mismo texto queda
		// transcrito en la sección "Hecho" del ticket 105 (evidencia
		// versionada en git, no un artefacto suelto que nadie vuelve a leer).
		System.out.println("[benchmark Carcomido] " + report.describe()); // NOSONAR: evidencia deliberada del benchmark

		assertThat(report.geometryComplexity()).isPositive();
		assertThat(report.featureCoverage().available()).isTrue();

		// Ticket 110 (HU-4): la métrica que destapó el problema real de
		// textura. A la densidad del ticket 109 ninguna cara no degenerada
		// puede quedar por debajo del mínimo legible -- si esto se rompe,
		// volvimos a generar caras de 1-2 téxeles donde la IA no puede
		// pintar nada, que es exactamente la regresión que costó una sesión
		// entera de diagnóstico manual descubrir.
		assertThat(report.faceArea()).as("el benchmark debe producir UV con área real").isNotNull();
		assertThat(report.faceArea().facesBelowMinimumLegible())
				.as("caras no degeneradas bajo %s px² (mín=%s, mediana=%s)", ModelGenerationQualityReport.MINIMUM_LEGIBLE_PX2,
						report.faceArea().minPx2(), report.faceArea().medianPx2())
				.isZero();
	}

	@Test
	void losRasgosCaracteristicosDelPersonajeQuedanRepresentadosEnLaGeometria_medidoPorCategoria_AC() throws Exception {
		UUID mobId = aMobWithCarcomidoReference();

		UUID jobId = mobGenerationService.startGeneration(mobId);
		assertThat(awaitTerminalStatus(jobId).getStatus()).isEqualTo("completed");
		MobProjectModel model = objectMapper.readValue(
				aiJobRepository.findById(jobId).orElseThrow().getProposalJson(), MobProjectModel.class);

		List<SemanticPartCategory> presentes = model.cuboids()
				.stream()
				.map(cuboid -> SemanticPartCategory.fromRawValue(cuboid.semanticPart()))
				.distinct()
				.toList();

		// Primero los rasgos que el documento de definición nombra
		// explícitamente como criterio visual del personaje (garras, ropa
		// desgarrada, mandíbula, grietas emisivas); después, que la anatomía
		// primaria determinista (097/098) siga ahí -- el personaje no es solo
		// un montón de rasgos sueltos.
		assertThat(presentes)
				.contains(
						SemanticPartCategory.CLAW, SemanticPartCategory.TORN_CLOTH, SemanticPartCategory.JAW,
						SemanticPartCategory.EMISSIVE_CRACK)
				.contains(SemanticPartCategory.HEAD, SemanticPartCategory.TORSO);
	}

	@Test
	void elModeloResultanteExportaABBModelYPasaLaValidacionFmmReal_AC() throws Exception {
		UUID mobId = aMobWithCarcomidoReference();

		UUID jobId = mobGenerationService.startGeneration(mobId);
		assertThat(awaitTerminalStatus(jobId).getStatus()).isEqualTo("completed");
		MobProjectModel model = objectMapper.readValue(
				aiJobRepository.findById(jobId).orElseThrow().getProposalJson(), MobProjectModel.class);

		ValidationResult validation = FmmCompatibilityValidator.validate(BBModelExporterV5.export(model));

		assertThat(validation.issues()).isEmpty();
		assertThat(validation.pass()).isTrue();
	}

}
