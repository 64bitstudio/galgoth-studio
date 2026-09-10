package com.galgothstudio.backend.domain.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Origen del contenido de una {@link UvRegion} en estado {@code PAINTED}
 * -- ticket 054, Diseño técnico §11/§21 y HU-37 AC #2 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md` ("el flujo de diff
 * Antes/Después muestra explícitamente que se sobrescribirá contenido
 * pintado a mano -- no solo contenido generado previamente por IA").
 *
 * <p><b>Hallazgo real, reportado explícitamente</b>: ni el diseño técnico
 * ni el propio ticket 054 fijan la forma exacta de esta distinción --
 * hasta este ticket, {@link UvRegionStatus#PAINTED} no lleva ninguna
 * información de origen (ticket 040 solo introdujo el estado, no quién lo
 * pintó). Sin este campo, HU-37 AC #2 sería imposible de cumplir
 * honestamente: "pintado a mano" y "generado por IA" serían
 * indistinguibles. Se agrega este campo ADITIVO (mismo mecanismo exacto
 * que {@code status} en 040 -- default seguro para JSON legacy, cero
 * cambio de forma para código existente) en vez de inventar un mecanismo
 * fuera de esquema (ej. inferir origen de `ai_jobs` por rango de tiempo,
 * heurística frágil y mucho más compleja).
 *
 * <p><b>Convención de default -- HAND es el valor seguro cuando no se
 * sabe</b>: {@code null} (el JSON legacy completo, y cualquier región marcada
 * {@code PAINTED} por el editor manual de pintado a mano ya existente,
 * ticket 047, que todavía no fue actualizado para escribir este campo)
 * se trata como "posiblemente pintado a mano" por el generador de diff
 * de este ticket ({@code TextureGenerationService}) -- nunca se subestima
 * el riesgo de sobrescribir trabajo real del usuario. Solo el propio
 * Apply de generación por IA de este ticket escribe {@code AI}
 * explícitamente sobre las caras que él mismo compuso.
 *
 * <p><b>Gap señalado para el Product Owner</b>: el editor de pintado
 * manual (047, ya mergeado a `dev`) no escribe este campo cuando el
 * usuario pinta a mano -- no hace falta para que este ticket sea
 * correcto (el default conservador `null`→HAND ya protege ese caso),
 * pero un ticket futuro debería hacer que el editor manual escriba
 * {@code HAND} explícitamente, para auditoría completa y para no
 * depender de una convención de default silenciosa indefinidamente.
 */
public enum UvPaintOrigin {
	@JsonProperty("hand") HAND,
	@JsonProperty("ai") AI

}
