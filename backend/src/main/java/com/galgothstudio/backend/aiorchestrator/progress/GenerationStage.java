package com.galgothstudio.backend.aiorchestrator.progress;

/**
 * Etapas reales del pipeline de generación (ticket 029, AC #1, master
 * prompt §5) -- valores de cadena (no un enum Java) porque viajan tal
 * cual como el campo `stage` de `ai_job_events`/`GenerationEventView`,
 * consumidos por el frontend como texto plano.
 *
 * <p>El mockup 03 del wizard (ticket 027) muestra 6 etapas, incluyendo
 * "Preparando UV…"/"Generando textura…" -- esas dos NO existen acá a
 * propósito: UV es determinista/automática (`AutoUv`, 006, nunca
 * decidida por IA) y la textura pintada es Fase 3, explícitamente fuera
 * de alcance de este ciclo (ver `docs/definiciones/galgoth-studio-mvp.md`).
 * Solo las 4 etapas que el AC de este ticket nombra existen de verdad.
 */
public final class GenerationStage {

	public static final String ANALIZANDO_REFERENCIA = "analizando_referencia";
	public static final String DETECTANDO_SILUETA = "detectando_silueta";
	public static final String CREANDO_RIG = "creando_rig";
	public static final String GENERANDO_CUBOIDES = "generando_cuboides";
	public static final String COMPLETADO = "completado";
	public static final String FALLIDO = "fallido";
	public static final String CANCELADO = "cancelado";

	private GenerationStage() {
	}

}
