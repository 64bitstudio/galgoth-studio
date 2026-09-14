package com.galgothstudio.backend.project;

/**
 * Ticket 084 -- una ruta que ahora exige dueño real (crear/listar "Mis
 * proyectos", mobs recientes) se llamó sin un {@code Authorization: Bearer}
 * válido. `SecurityConfig` sigue en {@code permitAll()} para estas rutas
 * hasta el ticket 085 (que introduce las reglas explícitas por ruta y el
 * enforcement dueño/público/privado sobre los 9 controladores anidados) --
 * este chequeo puntual en el controlador es la pieza mínima que HU-1/HU-2
 * necesitan ya: sin un {@code sub} real en el JWT no hay a quién ligar el
 * proyecto ni por quién filtrar el listado.
 */
public class UnauthenticatedRequestException extends RuntimeException {

	public UnauthenticatedRequestException() {
		super("Esta operación requiere haber iniciado sesión.");
	}

}
