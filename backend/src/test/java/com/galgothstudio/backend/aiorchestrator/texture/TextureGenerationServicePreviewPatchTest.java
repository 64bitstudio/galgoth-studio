package com.galgothstudio.backend.aiorchestrator.texture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobEventRepository;
import com.galgothstudio.backend.aiorchestrator.persistence.AiJobRepository;
import com.galgothstudio.backend.aiorchestrator.progress.GenerationEventBroadcaster;
import com.galgothstudio.backend.aiorchestrator.provider.ImageGenerationProvider;
import com.galgothstudio.backend.asset.AssetStorageService;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import com.galgothstudio.backend.project.draft.DraftPersistenceService;
import com.galgothstudio.backend.project.persistence.MobRepository;
import com.galgothstudio.backend.project.persistence.ReferenceImageRepository;
import com.galgothstudio.backend.project.texture.TextureService;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Ejercita {@link TextureGenerationService#previewPatchPayload} de forma
 * AISLADA (sin Testcontainers/Spring) para forzar el umbral de 32 KB del
 * esquema `preview_texture_patch` (ticket 054, Diseño técnico §13) --
 * imposible de forzar de forma confiable a través del pipeline completo:
 * el PNG sintético determinista de {@code MockImageProvider} es un
 * tablero de ajedrez, altamente compresible por PNG/DEFLATE sin importar
 * el tamaño pedido, así que nunca cruzaría el umbral. Acá se controla el
 * contenido directamente: ruido aleatorio real (incompresible) para el
 * caso "por encima del umbral", y un PNG mínimo para el caso "inline".
 */
class TextureGenerationServicePreviewPatchTest {

	private final AssetStorageService assetStorageService = mock(AssetStorageService.class);
	private final ObjectMapper objectMapper = new ObjectMapper();

	private TextureGenerationService newService() {
		return new TextureGenerationService(
				mock(MobRepository.class), mock(ReferenceImageRepository.class), assetStorageService, mock(DraftPersistenceService.class),
				mock(TexturePlanService.class), mock(TextureGenerationSheetPlanner.class), mock(TextureSheetSlicer.class),
				mock(TextureCompositorService.class), mock(ImageGenerationProvider.class), mock(TextureService.class), mock(AiJobRepository.class),
				mock(AiJobEventRepository.class), mock(GenerationEventBroadcaster.class), objectMapper, mock(Executor.class));
	}

	private static byte[] randomNoisePng(int size) {
		BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Random random = new Random(42);
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				image.setRGB(x, y, 0xFF000000 | random.nextInt(0x01000000));
			}
		}
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private static CuboidFacePlacement placementCovering(int size) {
		return new CuboidFacePlacement(
				"cube-1", FaceName.NORTH, new Vec4(0, 0, size, size), new Vec4(0, 0, size, size), new Vec3(size, size, size), "front");
	}

	@Test
	void payload_pequeno_va_inline_como_base64_y_nunca_sube_ningun_asset_AC3() throws Exception {
		byte[] smallAtlas = randomNoisePng(4); // 4x4 -- trivialmente por debajo de cualquier umbral razonable
		TextureGenerationService service = newService();

		JsonNode payload = service.previewPatchPayload(UUID.randomUUID(), new AtomicInteger(0), smallAtlas, List.of(placementCovering(4)));

		assertThat(payload.get("type").asText()).isEqualTo("preview_texture_patch");
		assertThat(payload.get("rect").get("width").asInt()).isEqualTo(4);
		assertThat(payload.get("rect").get("height").asInt()).isEqualTo(4);
		assertThat(payload.get("encoding").asText()).isEqualTo("base64");
		assertThat(payload.get("data").asText()).isNotBlank();
		assertThat(payload.has("url")).isFalse();

		// Verifica que el data inline decodifica de vuelta a un PNG real y válido.
		byte[] decoded = java.util.Base64.getDecoder().decode(payload.get("data").asText());
		assertThat(ImageIO.read(new ByteArrayInputStream(decoded))).isNotNull();

		verify(assetStorageService, never()).put(any(), any(), any());
	}

	@Test
	void payload_grande_por_encima_de_32kb_base64_sube_como_asset_temporal_y_emite_asset_url_AC3() {
		byte[] bigAtlas = randomNoisePng(150); // ruido real incompresible -- garantiza cruzar el umbral de 32 KB en base64
		assertThat(java.util.Base64.getEncoder().encodeToString(bigAtlas).getBytes())
				.as("fixture inválida si esto no supera el umbral -- ajustar el tamaño de randomNoisePng")
				.hasSizeGreaterThan(TextureGenerationService.INLINE_PREVIEW_MAX_BASE64_BYTES);
		TextureGenerationService service = newService();
		UUID jobId = UUID.randomUUID();

		JsonNode payload = service.previewPatchPayload(jobId, new AtomicInteger(3), bigAtlas, List.of(placementCovering(150)));

		assertThat(payload.get("encoding").asText()).isEqualTo("asset_url");
		assertThat(payload.has("data")).isFalse();
		assertThat(payload.get("url").asText()).isEqualTo("/api/texture-previews/" + jobId + "/3.png");

		verify(assetStorageService).put(eq("texture-previews/" + jobId + "/3.png"), any(byte[].class), eq("image/png"));
	}

	/** Nunca toca `textures/` (prefijo del bitmap definitivo, ticket 045) ni ninguna key fuera de `texture-previews/` -- ver Javadoc de {@link TextureGenerationService#PREVIEW_ASSET_PREFIX}. */
	@Test
	void el_asset_temporal_nunca_usa_el_prefijo_de_textura_definitiva() {
		byte[] bigAtlas = randomNoisePng(150);
		TextureGenerationService service = newService();

		service.previewPatchPayload(UUID.randomUUID(), new AtomicInteger(0), bigAtlas, List.of(placementCovering(150)));

		verify(assetStorageService)
				.put(org.mockito.ArgumentMatchers.argThat(key -> key.startsWith("texture-previews/") && !key.startsWith("textures/")), any(), any());
	}

}
