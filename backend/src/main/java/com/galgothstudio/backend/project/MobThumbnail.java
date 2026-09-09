package com.galgothstudio.backend.project;

/**
 * Una de "hasta 3" miniaturas de un proyecto en el dashboard (HU-02).
 * `thumbnailKey` es `null` mientras no exista pipeline de thumbnails
 * (ticket futuro) o la generación haya fallado -- el frontend renderiza
 * un placeholder genérico en ese caso (AC #5), nunca bloquea el listado.
 */
public record MobThumbnail(String mobId, String thumbnailKey) {
}
