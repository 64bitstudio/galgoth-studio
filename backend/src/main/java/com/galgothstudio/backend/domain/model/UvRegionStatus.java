package com.galgothstudio.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Estado de pintado de una {@link UvRegion} -- ticket 040, Diseño técnico
 * §1 de `docs/definiciones/galgoth-studio-fase3-textura.md`.
 *
 * <ul>
 * <li>{@code UNPAINTED}: región de un cuboid/cara vivo, sin arte real
 * todavía. Default de una región nueva y de cualquier región legacy
 * (Fase 1+2, sin este campo en su JSON almacenado -- ver {@link UvRegion}).</li>
 * <li>{@code PAINTED}: región de un cuboid/cara vivo con al menos un
 * píxel editado a mano o compuesto por IA. Flag explícito que la app
 * actualiza en el mismo commit que pinta -- nunca derivado por diff de
 * píxeles.</li>
 * <li>{@code ORPHAN}: fila cuyo {@code cuboidId} ya no existe en
 * {@code model.cuboids()} (cuboid eliminado). Se conserva solo para
 * bookkeeping de espacio ocupado; su {@code rect} original nunca se mueve.</li>
 * </ul>
 */
public enum UvRegionStatus {
	@JsonProperty("unpainted") UNPAINTED,
	@JsonProperty("painted") PAINTED,
	@JsonProperty("orphan") ORPHAN

}
