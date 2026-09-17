package com.galgothstudio.backend.aiorchestrator;

/**
 * Una advertencia estructurada del pipeline de generación -- ticket 116.
 *
 * <p><b>Qué es y qué no es</b>: no es un error (ninguna advertencia tumba
 * un job) ni una métrica del modelo resultante (para eso está
 * {@code ModelGenerationQualityReport}). Es el registro de una decisión
 * que el pipeline tomó sobre lo que la IA propuso: una pieza engrosada
 * porque era más fina que un téxel (121), una operación rechazada por los
 * constraints (099), un borde negro rellenado (114), un contenido de
 * textura sospechoso (102).
 *
 * <p><b>Por qué existe</b>: hasta este ticket, todo eso terminaba en
 * {@code log.info} y nada más. Tres tickets distintos dejaron escrito el
 * mismo pendiente ("exponerlos como generationWarnings estructurados queda
 * para un ticket futuro"), y el 121 se quedó sin poder cerrar un criterio
 * de aceptación por esto mismo.
 *
 * @param type    categoría estable, para poder filtrar sin parsear texto.
 * @param detail  qué pasó exactamente, en lenguaje del usuario -- incluye
 *                los números concretos cuando los hay.
 * @param subject qué elemento del modelo afecta (nombre de cuboid, bone o
 *                cara), o {@code null} si es del job entero.
 */
public record GenerationWarning(Type type, String detail, String subject) {

	public enum Type {
		/** Geometría propuesta por la IA que se engrosó al mínimo representable (ticket 121). */
		GEOMETRIA_ENGROSADA,
		/** Operación de geometría secundaria rechazada por los constraints (ticket 099). */
		GEOMETRIA_RECHAZADA,
		/** Bordes negros rellenados de forma determinista en una cara (ticket 114). */
		BORDES_RELLENADOS,
		/** Cara con una banda negra demasiado ancha para tratarla como costura -- se dejó intacta a propósito (ticket 114). */
		BANDA_NEGRA_ANCHA,
		/** Contenido de textura sospechoso: cobertura, contraste o paleta fuera de lo esperado (ticket 102). */
		CONTENIDO_SOSPECHOSO,
	}

	public GenerationWarning(Type type, String detail) {
		this(type, detail, null);
	}
}
