package com.galgothstudio.backend.asset;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Cliente genérico de almacenamiento de assets (ticket 023) -- MinIO
 * hoy, cualquier S3-compatible mañana. Reutilizable por otros assets
 * futuros (ej. imágenes de referencia, ticket 024) más allá de los
 * thumbnails que lo motivaron -- la lógica específica de "cómo se arma
 * la key de un thumbnail de mob" vive en `project.thumbnail`, no aquí.
 */
@Service
public class AssetStorageService {

	private final S3Client s3Client;
	private final String bucket;

	public AssetStorageService(S3Client s3Client, AssetStorageProperties properties) {
		this.s3Client = s3Client;
		this.bucket = properties.getBucket();
	}

	/** El bucket no existe hasta que alguien lo crea -- MinIO no lo autoprovisiona (a diferencia de spring-boot-docker-compose con Postgres). */
	@PostConstruct
	void ensureBucketExists() {
		try {
			s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
		} catch (NoSuchBucketException _) {
			s3Client.createBucket(builder -> builder.bucket(bucket));
		}
	}

	public void put(String key, byte[] content, String contentType) {
		s3Client.putObject(
				PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
				RequestBody.fromBytes(content));
	}

	/** @return vacío si la key no existe -- nunca lanza para ese caso puntual (distinto de un fallo real de storage). */
	public Optional<byte[]> get(String key) {
		try (ResponseInputStream<GetObjectResponse> response =
				s3Client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build())) {
			return Optional.of(response.readAllBytes());
		} catch (NoSuchKeyException _) {
			return Optional.empty();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
