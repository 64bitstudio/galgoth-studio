package com.galgothstudio.backend.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Ticket 100 -- HU-4. Presupuestos orientativos, nunca negativos. */
class GeometryDetailTest {

	@Test
	void secondaryBudget_restaLoQueLaPrimariaYaCubre() {
		assertThat(GeometryDetail.MEDIUM.secondaryBudget(14)).isEqualTo(45 - 14);
	}

	@Test
	void secondaryBudget_nuncaEsNegativo_siLaPrimariaYaExcedeElPresupuesto() {
		assertThat(GeometryDetail.LOW.secondaryBudget(100)).isZero();
	}

	@Test
	void isWithinBudget_respetaAmbosExtremos() {
		assertThat(GeometryDetail.MEDIUM.isWithinBudget(18)).isTrue();
		assertThat(GeometryDetail.MEDIUM.isWithinBudget(45)).isTrue();
		assertThat(GeometryDetail.MEDIUM.isWithinBudget(17)).isFalse();
		assertThat(GeometryDetail.MEDIUM.isWithinBudget(46)).isFalse();
	}

	@Test
	void losTresNivelesSonCrecientes() {
		assertThat(GeometryDetail.LOW.maxTotalCuboids()).isLessThan(GeometryDetail.MEDIUM.maxTotalCuboids());
		assertThat(GeometryDetail.MEDIUM.maxTotalCuboids()).isLessThan(GeometryDetail.HIGH.maxTotalCuboids());
	}
}
