package com.galgothstudio.backend.domain.export.validation;

/**
 * Hallazgo específico de {@link FmmCompatibilityValidator} -- nunca un
 * mensaje genérico (AC #3 del ticket 013): siempre nombra la regla y el
 * elemento concreto (uuid/nombre) al que aplica.
 *
 * @param severity {@link Severity#ERROR} bloquea el PASS; {@link Severity#WARNING} se reporta pero no bloquea.
 * @param rule     identificador corto y estable de la regla violada (para agrupar/filtrar).
 * @param element  uuid o nombre del elemento concreto (cuboid/bone) al que aplica el hallazgo.
 * @param message  explicación legible, específica de este caso -- no una plantilla vacía.
 */
public record ValidationIssue(Severity severity, String rule, String element, String message) {

	public enum Severity {
		ERROR, WARNING
	}

}
