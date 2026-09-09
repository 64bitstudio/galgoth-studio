package com.galgothstudio.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS para el origen local de desarrollo del frontend (Vite) --
 * `docs/definiciones/galgoth-studio-mvp.md` §9 confirma "CORS habilitado
 * para el origen local de desarrollo" como la estrategia elegida (sin
 * login/multi-tenancy este ciclo), no un proxy de Vite. Puertos 5173/5174
 * porque Vite salta al siguiente puerto libre si el 5173 ya está en uso
 * (ver sesiones de verificación en vivo de tickets anteriores).
 *
 * Ticket 035, hallazgo real post-despliegue (encontrado por Marco
 * navegando la app real, `POST /api/projects` -> 403): en despliegue
 * real, frontend y backend comparten origen (mismo contenedor, ver
 * `SpaResourceConfig`) -- pero Spring SÍ aplica su filtro CORS de todas
 * formas, porque `CorsUtils.isCorsRequest` solo mira si la cabecera
 * `Origin` está PRESENTE, nunca si coincide con el propio host de la
 * request. El navegador manda `Origin` en un `POST` con
 * `Content-Type: application/json` aunque sea mismo origen (no es un
 * request "simple" del Fetch spec) -- confirmado real reproduciendo con
 * `curl -H "Origin: https://studio-dev.galgoth.64bitstudio.com"` contra
 * el subdominio real (403) vs. sin esa cabecera (201, éxito). Como ese
 * dominio no estaba en `allowedOrigins`, `DefaultCorsProcessor` lo
 * rechaza con 403 explícito. Fix: `galgoth.cors.allowed-origins`
 * (relaxed binding, mismo patrón que el resto del proyecto) agrega los
 * 3 subdominios reales -- `application-deploy.properties` los declara
 * como default para el perfil de despliegue, sin tocar el
 * comportamiento local (`application.properties` conserva los puertos
 * de Vite).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Value("${galgoth.cors.allowed-origins}")
	private String[] allowedOrigins;

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		// `allowedHeaders("*")`/`OPTIONS` explícitos (antes implícitos vía el
		// manejo automático de preflight de Spring) -- ticket 033, hallazgo
		// real en CI: un fetch con `Content-Type: application/json` desde un
		// navegador corriendo en un contenedor Docker hermano (namespace de
		// red compartido con el agente) recibía el preflight OPTIONS sin
		// ninguna cabecera CORS de vuelta. Nunca reproducido en desarrollo
		// local (mismo código, misma config, decenas de verificaciones en
		// vivo) -- ser explícito acá no cambia el comportamiento ya probado
		// localmente, pero descarta esta hipótesis concreta sin adivinar más.
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*");
	}

}
