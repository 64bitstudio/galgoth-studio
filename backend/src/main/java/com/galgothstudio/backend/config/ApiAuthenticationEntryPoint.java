package com.galgothstudio.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.project.UnauthenticatedRequestException;
import com.galgothstudio.backend.project.api.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Ticket 085 -- sin esto, un `401` que Spring Security rechaza ANTES de
 * llegar a un controlador (rutas ahora {@code authenticated()}, ej.
 * {@code POST /api/projects} sin `Authorization`) sale con cuerpo vacío,
 * un formato de error distinto al resto de la API. Mismo mensaje/forma
 * EXACTA que {@link UnauthenticatedRequestException} vía
 * {@code ApiExceptionHandler} (el caso, hasta el ticket 084, en que esa
 * excepción SÍ llegaba a un controlador con `SecurityConfig` en
 * `permitAll()`) -- para el cliente no debería importar cuál de los dos
 * mecanismos rechazó la request.
 */
@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final ObjectMapper objectMapper;

	public ApiAuthenticationEntryPoint(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
			throws IOException {
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		ApiErrorResponse body =
				new ApiErrorResponse("UNAUTHENTICATED", new UnauthenticatedRequestException().getMessage(), null);
		objectMapper.writeValue(response.getWriter(), body);
	}

}
