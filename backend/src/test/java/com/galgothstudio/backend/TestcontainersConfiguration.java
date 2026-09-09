package com.galgothstudio.backend;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));
	}

	/**
	 * MinIO real, compartido por TODA la suite de test (ticket 023) -- sin
	 * `@ServiceConnection` (Spring Boot no trae un connector nativo para
	 * S3). Arranca eager en un inicializador estático (no en el método
	 * `@Bean`, que corre demasiado tarde) y publica sus credenciales/
	 * endpoint reales como *System properties* aquí mismo -- nunca el
	 * MinIO persistente de `docker/docker-compose.yml`, que es solo para
	 * dev manual.
	 *
	 * Hallazgo real corregido en este ticket: `AssetStorageService` (paquete
	 * `asset`) tiene un `@PostConstruct` que intenta conectar a MinIO --
	 * como ese bean lo crea CUALQUIER `@SpringBootTest` que arranque el
	 * contexto completo (component scan normal, no solo los tests de
	 * thumbnails), un `@DynamicPropertySource` declarado SOLO en
	 * `MobThumbnailControllerTest` (como en un primer intento de este mismo
	 * ticket) deja a TODOS los demás tests de `@SpringBootTest` apuntando
	 * al `application.properties` de dev (`localhost:9000`) -- rompiéndolos
	 * en cualquier máquina/CI donde ese MinIO de dev no esté corriendo.
	 * Publicar las propiedades acá, en el `static` que ya carga esta clase
	 * (vía `@Import(TestcontainersConfiguration.class)`, el mismo patrón ya
	 * usado por TODOS los tests de `@SpringBootTest` de este proyecto),
	 * replica para MinIO la misma garantía que `@ServiceConnection` ya le
	 * da a Postgres arriba: cualquier test que importe esta clase queda
	 * automáticamente bien configurado, la use o no explícitamente.
	 */
	public static final MinIOContainer MINIO_CONTAINER = new MinIOContainer(DockerImageName.parse("minio/minio:latest"));

	static {
		MINIO_CONTAINER.start();
		System.setProperty("galgoth.storage.minio.endpoint", MINIO_CONTAINER.getS3URL());
		System.setProperty("galgoth.storage.minio.access-key", MINIO_CONTAINER.getUserName());
		System.setProperty("galgoth.storage.minio.secret-key", MINIO_CONTAINER.getPassword());
		System.setProperty("galgoth.storage.minio.bucket", "galgoth-studio-test-assets");
	}

	@Bean
	MinIOContainer minioContainer() {
		return MINIO_CONTAINER;
	}

}
