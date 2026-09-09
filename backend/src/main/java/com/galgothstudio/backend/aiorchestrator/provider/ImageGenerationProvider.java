package com.galgothstudio.backend.aiorchestrator.provider;

/**
 * Genera una imagen a partir de un prompt de texto -- interfaz estable
 * pensada para la generación de textura pintada por IA (master prompt
 * §12), explícitamente **Fase 3, fuera de alcance de este Technical
 * Alpha** (`docs/definiciones/galgoth-studio-mvp.md`). Se define aquí
 * (ticket 025) solo para que agregar un proveedor real después no
 * requiera tocar `ai-orchestrator` ni el dominio -- ningún proveedor
 * real la implementa este ciclo (`ClaudeProvider` no genera imágenes;
 * ni Claude lo soporta). `MockProvider` sí la implementa, para dejar la
 * abstracción completa y testeable cuando el consumidor real llegue.
 */
public interface ImageGenerationProvider {

	byte[] generateImage(String prompt);

}
