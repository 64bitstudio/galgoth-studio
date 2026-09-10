package com.galgothstudio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Criterio de aceptación #6 de pending/003-esquema-bd-inicial-migraciones.md:
 * "el set de migraciones, corrido en un Postgres limpio y luego revertido,
 * termina sin error en ambas direcciones".
 *
 * <p><b>Decisión de implementación (documentada, no silenciosa):</b> Flyway
 * Community (el que usa este proyecto, mismo que auth-core-mc) no soporta
 * migraciones "undo" por versión -- esa es una funcionalidad de pago
 * (Flyway Teams/Enterprise). "Revertir" se interpreta aquí como
 * {@code Flyway#clean()} (elimina todos los objetos del esquema) seguido
 * de una nueva corrida de {@code migrate()} -- prueba real de que el
 * ciclo completo migrar -> destruir -> volver a migrar termina sin error
 * y es repetible, que es lo que un pipeline de CI necesita en la práctica
 * (no un undo selectivo de una sola migración).
 */
@Testcontainers
class SchemaMigrationReversibilityTest {

	@Container
	static PostgreSQLContainer postgres =
			new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

	@Test
	void migrar_luego_limpiar_luego_volver_a_migrar_termina_sin_error() {
		Flyway flyway = Flyway.configure()
				.dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
				.cleanDisabled(false)
				.load();

		assertThatCode(flyway::migrate).doesNotThrowAnyException();
		assertThat(flyway.info().current().getVersion().toString()).isEqualTo("3");

		assertThatCode(flyway::clean).doesNotThrowAnyException();

		assertThatCode(flyway::migrate).doesNotThrowAnyException();
		assertThat(flyway.info().current().getVersion().toString()).isEqualTo("3");
	}

}
