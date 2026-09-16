package com.galgothstudio.backend.domain.template;

/**
 * Rango válido [min,max] para un multiplicador de proporción (ej.
 * {@code headScale}, {@code armLength}) -- ticket 097, HU-3. Evita tanto un
 * template rígido (siempre igual) como estructuras anatómicamente
 * imposibles si {@code ModelIntent} reporta un valor extremo.
 */
public record ProportionRange(double min, double max) {

	public ProportionRange {
		if (min <= 0 || max <= 0) {
			throw new IllegalArgumentException("ProportionRange requiere min/max positivos, recibió [" + min + "," + max + "]");
		}
		if (min > max) {
			throw new IllegalArgumentException("ProportionRange requiere min <= max, recibió [" + min + "," + max + "]");
		}
	}

	/** Clampa {@code value} a este rango. */
	public double clamp(double value) {
		return Math.clamp(value, min, max);
	}

	public boolean contains(double value) {
		return value >= min && value <= max;
	}
}
