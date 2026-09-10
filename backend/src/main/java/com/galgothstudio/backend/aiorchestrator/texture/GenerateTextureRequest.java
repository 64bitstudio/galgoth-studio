package com.galgothstudio.backend.aiorchestrator.texture;

/**
 * Body de `POST /api/mobs/{mobId}/ai/generate-texture` (ticket 054, HU-36/
 * HU-37). Campos como {@code String} crudo (no el enum Java directo) --
 * validados explícitamente por {@link TextureGenerationService} vía
 * {@link TextureStyle#fromWireValue}/{@link TextureDetailLevel#fromWireValue},
 * para que un valor inválido responda `400 INVALID_TEXTURE_GENERATION_REQUEST`
 * (nuestro {@code ApiErrorResponse} de siempre) en vez del 400 genérico de
 * Spring por un fallo de deserialización de enum.
 *
 * @param boneId {@code null}/ausente -&gt; HU-36 (genera TODOS los bones
 *               con geometría, {@code job_type=generate_texture}); no
 *               nulo -&gt; HU-37 (regenera ese bone puntual,
 *               {@code job_type=edit_texture}).
 */
public record GenerateTextureRequest(String style, String detailLevel, String boneId) {
}
