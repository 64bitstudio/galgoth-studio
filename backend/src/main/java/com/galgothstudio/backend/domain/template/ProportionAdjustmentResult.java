package com.galgothstudio.backend.domain.template;

import java.util.List;

/**
 * Resultado de aplicar proporciones a un {@link CanonicalTemplate} -- ticket
 * 097, HU-3. {@code warnings} nunca provoca que se descarte el ajuste: un
 * valor fuera de rango se clampa y se registra aquí (master prompt: "nunca
 * se descarta el intent completo en silencio").
 */
public record ProportionAdjustmentResult(List<TemplateBoneSpec> bones, List<TemplateCuboidSpec> cuboids, List<String> warnings) {
}
