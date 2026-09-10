package com.galgothstudio.backend.domain.model;

/**
 * Paleta dominante/acento extraída de la imagen de referencia (ticket
 * 052, Diseño técnico §11 de `docs/definiciones/galgoth-studio-fase3-textura.md`)
 * -- GLOBAL para el {@link TexturePlan} completo, no por bone: es una
 * propiedad de la imagen de concept art analizada, no de una parte
 * puntual del modelo. Colores hex `#RRGGBB` (nunca nombres de color en
 * texto libre) para que `TextureGenerationSheetPlanner` (054) los use
 * directo en la composición de prompt sin tener que interpretar
 * lenguaje natural.
 */
public record TexturePalette(String dominantColorHex, String accentColorHex) {
}
