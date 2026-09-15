package com.galgothstudio.backend.account;

import java.util.Arrays;

/**
 * Bytes reales de un avatar ya subido más su content-type real (nunca
 * inferido del nombre de archivo) -- mismo patrón que
 * {@code StoredReferenceImage} (ticket 024), incluida la razón de
 * sobreescribir `equals`/`hashCode`/`toString` (Sonar `S6218`: un record
 * con un campo `byte[]` compara por identidad de array, no por
 * contenido, por defecto).
 */
public record StoredAvatar(byte[] content, String contentType) {

	@Override
	public boolean equals(Object other) {
		if (this == other) {
			return true;
		}
		if (!(other instanceof StoredAvatar(byte[] otherContent, String otherContentType))) {
			return false;
		}
		return Arrays.equals(content, otherContent) && contentType.equals(otherContentType);
	}

	@Override
	public int hashCode() {
		return 31 * Arrays.hashCode(content) + contentType.hashCode();
	}

	@Override
	public String toString() {
		return "StoredAvatar[content=" + content.length + " bytes, contentType=" + contentType + "]";
	}

}
