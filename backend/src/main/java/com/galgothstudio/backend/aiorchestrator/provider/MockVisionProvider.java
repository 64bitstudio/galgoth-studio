package com.galgothstudio.backend.aiorchestrator.provider;

/**
 * Doble determinista de {@link VisionModelProvider} (master prompt §20,
 * ticket 025) -- SIN llamar a ningún servicio externo. Uso EXCLUSIVO en
 * tests/desarrollo (`AI_VISION_PROVIDER=mock`, ver `AiProviderConfig`).
 * Clase separada de `MockReasoningProvider`/`MockImageProvider` a
 * propósito -- ver el hallazgo real documentado en `AiProviderConfig`.
 *
 * "Configurable" (AC del ticket): un test que necesite un `rawContent`
 * específico llama {@link #setNextResponse(String)} antes de ejercitar
 * el código bajo prueba. Sin configurar explícitamente, devuelve el
 * ejemplo literal de `ModelIntent` del master prompt §9.1 en vez de una
 * cadena vacía -- útil como smoke-test por defecto.
 */
public class MockVisionProvider implements VisionModelProvider {

	private static final String PROVIDER_NAME = "mock";
	private static final String DEFAULT_MODEL = "mock-model";

	private static final String DEFAULT_RESPONSE =
			"""
			{
			  "silhouette": "hunched humanoid",
			  "proportions": {
			    "headScale": 1.08,
			    "armLength": 1.18,
			    "handScale": 1.30,
			    "shoulderWidth": 1.10
			  },
			  "asymmetry": 0.72,
			  "features": ["oversized hands", "ragged shoulder cloth", "damaged lower garment"],
			  "materials": ["desaturated grey-green skin", "dark brown torn cloth", "violet emissive cracks"]
			}
			""";

	private String nextResponse = DEFAULT_RESPONSE;
	private Runnable onCall = () -> {
	};

	public void setNextResponse(String rawJson) {
		this.nextResponse = rawJson;
	}

	/**
	 * Hook de test (ticket 029) -- ejecutado sincrónicamente ANTES de
	 * devolver la respuesta configurada. Único uso real: el test de
	 * cancelación de {@code MobGenerationServiceTest} lo usa para
	 * bloquear este método (vía un `CountDownLatch`) hasta que el hilo
	 * del test dispare {@code requestCancellation} sobre el job que ya
	 * está corriendo en OTRO hilo -- sin este hook no habría forma
	 * determinista (sin sleeps a ciegas) de "atrapar" el pipeline en
	 * pleno vuelo. Default no-op -- ningún otro test se ve afectado.
	 */
	public void setOnCall(Runnable onCall) {
		this.onCall = onCall;
	}

	@Override
	public AiProviderResponse analyzeReferenceImage(VisionAnalysisRequest request) {
		onCall.run();
		return new AiProviderResponse(nextResponse, PROVIDER_NAME, DEFAULT_MODEL, request.promptVersion(), request.schemaVersion());
	}

}
