package com.galgothstudio.backend.aiorchestrator.provider;

import static org.assertj.core.api.Assertions.assertThat;

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

}
