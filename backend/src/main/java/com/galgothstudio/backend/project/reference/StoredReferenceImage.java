package com.galgothstudio.backend.project.reference;

import java.util.Arrays;

/**
 * Bytes crudos + content-type real de una imagen de referencia ya
 * subida -- a diferencia del thumbnail (siempre PNG), aquí el
 * content-type varía por archivo (PNG o JPEG), así que debe viajar
 * junto con los bytes.
 *
 * `equals`/`hashCode`/`toString` sobreescritos a propósito (Sonar
 * `S6218`): el `equals`/`hashCode` que Java genera para un record con un
 * campo `byte[]` compara por IDENTIDAD del array, no por contenido -- y
 * el `toString` por defecto imprimiría algo inútil tipo `[B@1a2b3c`. Este
 * tipo nunca se compara por igualdad en producción, pero la corrección
 * es gratis y evita una trampa real si alguna vez se usa en un test o
 * un `Set`/`Map`.
 */
public record StoredReferenceImage(byte[] content, String contentType) {

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof StoredReferenceImage that)) {
			return false;
		}
		return Arrays.equals(content, that.content) && contentType.equals(that.contentType);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(content) + contentType.hashCode();
	}

	@Override
	public String toString() {
		return "StoredReferenceImage[content=" + content.length + " bytes, contentType=" + contentType + "]";
	}

}
