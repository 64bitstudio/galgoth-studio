package com.galgothstudio.backend.aiorchestrator.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.galgothstudio.backend.TestcontainersConfiguration;
import com.galgothstudio.backend.asset.AssetStorageService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

/** Ticket 054, Diseño técnico §13 -- sirve exclusivamente los assets temporales de `preview_texture_patch` bajo `texture-previews/`. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TexturePreviewAssetControllerTest {

	@Autowired
	private org.springframework.test.web.servlet.MockMvc mockMvc;

	@Autowired
	private AssetStorageService assetStorageService;

	@Test
	void devuelve_el_asset_temporal_ya_subido() throws Exception {
		UUID jobId = UUID.randomUUID();
		byte[] pngBytes = {1, 2, 3, 4};
		assetStorageService.put("texture-previews/" + jobId + "/5.png", pngBytes, "image/png");

		mockMvc.perform(get("/api/texture-previews/{jobId}/{fileName}", jobId, "5.png"))
				.andExpect(status().isOk())
				.andExpect(content().contentType(MediaType.IMAGE_PNG))
				.andExpect(content().bytes(pngBytes));
	}

	@Test
	void responde_404_si_el_asset_no_existe() throws Exception {
		mockMvc.perform(get("/api/texture-previews/{jobId}/{fileName}", UUID.randomUUID(), "no-existe.png")).andExpect(status().isNotFound());
	}

}
