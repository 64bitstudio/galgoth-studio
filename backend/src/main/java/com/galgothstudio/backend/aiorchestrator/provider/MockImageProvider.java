package com.galgothstudio.backend.aiorchestrator.provider;

/**
 * Doble determinista de {@link ImageGenerationProvider} -- sin proveedor
 * real este ciclo (ver esa interfaz), pero se implementa igual para que
 * la abstracción quede completa/testeable cuando el consumidor real
 * llegue (Fase 3, fuera de alcance del Technical Alpha). Sin
 * `@ConditionalOnProperty`/exposición en `AiProviderConfig` -- nada la
 * selecciona todavía, se instancia directo donde haga falta.
 */
public class MockImageProvider implements ImageGenerationProvider {

	private byte[] nextImage = new byte[0];

	public void setNextImage(byte[] imageBytes) {
		this.nextImage = imageBytes;
	}

	@Override
	public byte[] generateImage(String prompt) {
		return nextImage;
	}

}
