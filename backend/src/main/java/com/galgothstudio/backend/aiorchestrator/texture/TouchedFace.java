package com.galgothstudio.backend.aiorchestrator.texture;

import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.Vec4;

/**
 * Una cara tocada por una generación/regeneración de textura por IA
 * (ticket 054) -- unidad del diff Antes/Después (HU-38) y de la
 * detección de sobrescritura de contenido pintado a mano (HU-37 AC #2).
 *
 * @param rect                  {@code atlasUvRect} de esta cara (destino real en el atlas).
 * @param handPaintedOverwrite  {@code true} si esta cara YA estaba {@code PAINTED} con origen
 *                              pintado a mano o desconocido (ver {@code UvPaintOrigin}) antes de
 *                              que esta generación la reemplace -- {@code false} si estaba
 *                              {@code UNPAINTED} o {@code PAINTED} con origen {@code AI} conocido.
 */
public record TouchedFace(String cuboidId, FaceName face, Vec4 rect, boolean handPaintedOverwrite) {
}
