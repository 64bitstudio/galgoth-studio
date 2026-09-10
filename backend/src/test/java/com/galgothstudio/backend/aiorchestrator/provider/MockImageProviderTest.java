package com.galgothstudio.backend.aiorchestrator.provider;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.aiorchestrator.provider.ImageGenerationProvider.TextureGenerationSheetRequest;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MockImageProviderTest {

	@Test
	void devuelve_bytes_vacios_por_defecto_y_es_configurable() {
		MockImageProvider provider = new MockImageProvider();
		assertThat(provider.generateImage("un atardecer")).isEmpty();

		byte[] configured = {9, 9, 9};
		provider.setNextImage(configured);
		assertThat(provider.generateImage("un atardecer")).isEqualTo(configured);
	}

	/** AC del ticket 051: "doble determinista real", no solo una interfaz sin comportamiento -- el PNG sintético default debe ser decodificable y tener EXACTAMENTE las dimensiones pedidas. */
	@Test
	void generateTextureSheet_sin_configurar_devuelve_un_png_sintetico_con_las_dimensiones_exactas_pedidas() throws IOException {
		MockImageProvider provider = new MockImageProvider();

		byte[] pngBytes = provider.generateTextureSheet(new TextureGenerationSheetRequest("goblin", null, 40, 24, "pixel-art"));

		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(pngBytes));
		assertThat(decoded.getWidth()).isEqualTo(40);
		assertThat(decoded.getHeight()).isEqualTo(24);
	}

	@Test
	void generateTextureSheet_sin_configurar_es_determinista_mismo_request_mismos_bytes() {
		MockImageProvider provider = new MockImageProvider();
		TextureGenerationSheetRequest request = new TextureGenerationSheetRequest("goblin", null, 40, 24, "pixel-art");

		byte[] first = provider.generateTextureSheet(request);
		byte[] second = provider.generateTextureSheet(request);

		assertThat(first).isEqualTo(second);
	}

	@Test
	void generateTextureSheet_sin_configurar_distinto_prompt_o_estilo_da_bytes_distintos() {
		MockImageProvider provider = new MockImageProvider();
		byte[] a = provider.generateTextureSheet(new TextureGenerationSheetRequest("goblin", null, 16, 16, "pixel-art"));
		byte[] b = provider.generateTextureSheet(new TextureGenerationSheetRequest("goblin", null, 16, 16, "realista"));

		assertThat(a).isNotEqualTo(b);
	}

	@Test
	void generateTextureSheet_es_configurable_via_setNextTextureSheet() {
		MockImageProvider provider = new MockImageProvider();
		byte[] configured = {7, 7, 7};

		provider.setNextTextureSheet(configured);

		assertThat(provider.generateTextureSheet(new TextureGenerationSheetRequest("goblin", null, 16, 16, null))).isEqualTo(configured);
	}

}
