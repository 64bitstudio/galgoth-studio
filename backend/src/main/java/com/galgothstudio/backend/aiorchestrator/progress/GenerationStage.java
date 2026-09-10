package com.galgothstudio.backend.aiorchestrator.progress;

/**
 * Etapas reales del pipeline de generación (ticket 029, AC #1, master
 * prompt §5) -- valores de cadena (no un enum Java) porque viajan tal
 * cual como el campo `stage` de `ai_job_events`/`GenerationEventView`,
 * consumidos por el frontend como texto plano.
 *
 * <p>El mockup 03 del wizard (ticket 027) muestra 7 etapas, incluyendo
 * "Preparando UV…"/"Generando textura…"/"Optimizando modelo…" -- esas
 * TRES NO existen acá a propósito: UV es determinista/automática
 * (`AutoUv`, 006, nunca decidida por IA) y la textura pintada es Fase 3,
 * explícitamente fuera de alcance de este ciclo (ver
 * `docs/definiciones/galgoth-studio-mvp.md`).
 *
 * <p><b>Ticket 038 (bugfix del progreso IA)</b>: agrega
 * {@link #VALIDANDO_GEOMETRIA}/{@link #PREPARANDO_RESULTADO} como dos
 * etapas reales más, insertadas en el pipeline real ANTES de
 * `completado` (ver `MobGenerationService.runPipeline`) -- ya no son
 * solo etiquetas del mockup sin contraparte real: envuelven trabajo que
 * el pipeline ya hacía en silencio (aplicar UV final, correr
 * `FmmCompatibilityValidator`).
 */
public final class GenerationStage {

	public static final String ANALIZANDO_REFERENCIA = "analizando_referencia";
	public static final String DETECTANDO_SILUETA = "detectando_silueta";
	public static final String CREANDO_RIG = "creando_rig";
	public static final String GENERANDO_CUBOIDES = "generando_cuboides";
	public static final String PREPARANDO_RESULTADO = "preparando_resultado";
	public static final String VALIDANDO_GEOMETRIA = "validando_geometria";
	public static final String COMPLETADO = "completado";
	public static final String FALLIDO = "fallido";
	public static final String CANCELADO = "cancelado";

	/**
	 * Ticket 054 (Diseño técnico §13 de
	 * `docs/definiciones/galgoth-studio-fase3-textura.md`, HU-36) -- etapas
	 * nuevas del pipeline de generación/regeneración de TEXTURA por IA
	 * (`TextureGenerationService`), reutilizando `ai_job_events`/SSE tal
	 * cual. {@code GENERANDO_BONE_PREFIX} es la base de un valor DINÁMICO
	 * (a diferencia de todas las demás constantes de esta clase, fijas) --
	 * el propio Diseño técnico lo describe como {@code generando_bone_X},
	 * donde {@code X} es el bone real que se está generando en ese
	 * momento; ver {@code TextureGenerationService#generatingBoneStage}.
	 */
	public static final String ANALIZANDO_PALETA = "analizando_paleta";
	public static final String MAPEANDO_CARAS = "mapeando_caras";
	public static final String GENERANDO_BONE_PREFIX = "generando_bone_";
	public static final String COMPONIENDO_ATLAS = "componiendo_atlas";
	public static final String LIMPIANDO_PIXELES = "limpiando_pixeles";

	private GenerationStage() {
	}

}
