package com.galgothstudio.backend.project.reference;

/**
 * Bytes crudos + content-type real de una imagen de referencia ya
 * subida -- a diferencia del thumbnail (siempre PNG), aquí el
 * content-type varía por archivo (PNG o JPEG), así que debe viajar
 * junto con los bytes. Clase simple, no `record`: un campo `byte[]`
 * en un record dispara el mismo tipo de hallazgo de Sonar que
 * `Vec3`/`Vec4` con `double[]` (ticket 004, `S2384`) por la identidad
 * de `equals`/`hashCode` que Java genera para arrays -- este tipo nunca
 * se compara por igualdad, así que un getter simple evita el problema
 * de raíz en vez de suprimir la regla.
 */
public final class StoredReferenceImage {

	private final byte[] content;
	private final String contentType;

	public StoredReferenceImage(byte[] content, String contentType) {
		this.content = content;
		this.contentType = contentType;
	}

	public byte[] getContent() {
		return content;
	}

	public String getContentType() {
		return contentType;
	}

}
