package com.galgothstudio.backend.project.reference;

/**
 * Bytes crudos + content-type real de una imagen de referencia ya
 * subida -- a diferencia del thumbnail (siempre PNG), aquí el
 * content-type varía por archivo (PNG o JPEG), así que debe viajar
 * junto con los bytes. Nunca se compara por igualdad (`equals`), así
 * que la identidad-de-array que Java genera para el campo `content` en
 * un record no es un problema real aquí -- a diferencia de `Vec3`/`Vec4`
 * (ticket 004, `S2384`), donde sí se comparaban por valor.
 */
public record StoredReferenceImage(byte[] content, String contentType) {
}
