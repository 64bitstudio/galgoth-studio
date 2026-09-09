package com.galgothstudio.backend.domain.export.validation;

import java.util.List;

/**
 * Resultado de {@link FmmCompatibilityValidator#validate}. {@link #pass()}
 * es explícito (AC #4 del ticket 013) -- {@code true} solo si no hay
 * {@link ValidationIssue.Severity#ERROR} entre {@code issues} (los
 * {@code WARNING} se reportan igual pero no bloquean el export).
 */
public record ValidationResult(List<ValidationIssue> issues) {

	public boolean pass() {
		return issues.stream().noneMatch(issue -> issue.severity() == ValidationIssue.Severity.ERROR);
	}

	public List<ValidationIssue> errors() {
		return issues.stream().filter(issue -> issue.severity() == ValidationIssue.Severity.ERROR).toList();
	}

	public List<ValidationIssue> warnings() {
		return issues.stream().filter(issue -> issue.severity() == ValidationIssue.Severity.WARNING).toList();
	}

}
