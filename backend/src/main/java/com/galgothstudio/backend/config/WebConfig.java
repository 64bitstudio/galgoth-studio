package com.galgothstudio.backend.config;

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
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

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
				.allowedOrigins("http://localhost:5173", "http://localhost:5174")
				.allowedMethods("GET", "POST", "PATCH", "DELETE", "OPTIONS")
				.allowedHeaders("*");
	}

}
