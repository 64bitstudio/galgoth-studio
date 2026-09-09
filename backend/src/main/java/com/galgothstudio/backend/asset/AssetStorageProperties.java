package com.galgothstudio.backend.asset;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** `galgoth.storage.minio.*` en `application.properties` (ticket 023). */
@ConfigurationProperties(prefix = "galgoth.storage.minio")
public class AssetStorageProperties {

	private String endpoint;
	private String accessKey;
	private String secretKey;
	private String bucket;

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getAccessKey() {
		return accessKey;
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	public String getSecretKey() {
		return secretKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public String getBucket() {
		return bucket;
	}

	public void setBucket(String bucket) {
		this.bucket = bucket;
	}

}
