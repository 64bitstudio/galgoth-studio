package com.galgothstudio.backend.asset;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * MinIO (S3-compatible, ticket 023) -- el AWS SDK v2 S3 client funciona
 * contra cualquier endpoint S3-compatible con `endpointOverride` +
 * `forcePathStyle(true)` (MinIO no soporta el estilo virtual-hosted-style
 * por defecto del SDK, que asume subdominios por bucket).
 */
@Configuration
@EnableConfigurationProperties(AssetStorageProperties.class)
public class MinioConfig {

	@Bean
	public S3Client s3Client(AssetStorageProperties properties) {
		return S3Client.builder()
				.endpointOverride(URI.create(properties.getEndpoint()))
				// MinIO no tiene regiones reales, pero el SDK exige una -- cualquier valor válido sirve.
				.region(Region.US_EAST_1)
				.credentialsProvider(StaticCredentialsProvider.create(
						AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())))
				.forcePathStyle(true)
				.build();
	}

}
