package com.galgothstudio.backend.domain.export.bbmodel;

/**
 * Entrada del array {@code textures} -- {@code source} es una data URI PNG
 * embebida (`data:image/png;base64,...`), campos mínimos verificados
 * contra `Texture.properties`/`getSaveCopy()` del código fuente real de
 * Blockbench (`js/texturing/textures.js`) para que cargue sin diálogo de
 * reparación. La POSICIÓN de esta entrada en el array {@code textures} es
 * lo que {@code Face.texture} referencia como índice (verificado en
 * `Face.getSaveCopy()`: {@code Texture.all.indexOf(tex)}), no el campo
 * {@code id} -- por eso este exportador solo emite un único texture (índice 0,
 * el mismo que asigna {@code AlphaAutoPackStrategy}).
 */
public record BBTexture(String uuid, String name, String id, boolean particle, int width, int height, String source) {
}
