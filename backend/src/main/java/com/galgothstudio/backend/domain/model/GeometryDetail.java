package com.galgothstudio.backend.domain.model;

/**
 * Presupuesto de detalle geométrico -- ticket 100, HU-4. Presupuestos
 * ORIENTATIVOS de cuboides totales (primaria + secundaria), no cuotas
 * rígidas: el pipeline nunca falla por estar fuera de rango, solo lo
 * registra como advertencia (ver {@code MobGenerationService}).
 *
 * @param minTotalCuboids extremo inferior del presupuesto total (primaria + secundaria).
 * @param maxTotalCuboids extremo superior del presupuesto total.
 */
public enum GeometryDetail {

	LOW(8, 18),
	MEDIUM(18, 45),
	HIGH(35, 80);

	private final int minTotalCuboids;
	private final int maxTotalCuboids;

	GeometryDetail(int minTotalCuboids, int maxTotalCuboids) {
		this.minTotalCuboids = minTotalCuboids;
		this.maxTotalCuboids = maxTotalCuboids;
	}

	public int minTotalCuboids() {
		return minTotalCuboids;
	}

	public int maxTotalCuboids() {
		return maxTotalCuboids;
	}

	/**
	 * Presupuesto de cuboides SECUNDARIOS para {@code SecondaryGeometryPlanner}
	 * -- el extremo superior del presupuesto total menos lo que la anatomía
	 * primaria ya cubre, nunca negativo (un template con más primaria que
	 * el presupuesto elegido simplemente no deja margen para secundaria).
	 */
	public int secondaryBudget(int primaryCuboidCount) {
		return Math.max(0, maxTotalCuboids - primaryCuboidCount);
	}

	public boolean isWithinBudget(int totalCuboidCount) {
		return totalCuboidCount >= minTotalCuboids && totalCuboidCount <= maxTotalCuboids;
	}
}
