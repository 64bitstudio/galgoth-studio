package com.galgothstudio.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GalgothStudioApplicationTests {

	@Test
	void contextLoads() {
		// El contexto solo levanta si Flyway migró V1__init_schema.sql sin
		// error contra el Postgres real de Testcontainers (Spring Boot corre
		// Flyway antes de terminar de inicializar el DataSource/JPA).
	}

}
