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
		registry.addMapping("/api/**")
				.allowedOrigins("http://localhost:5173", "http://localhost:5174")
				.allowedMethods("GET", "POST", "PATCH", "DELETE");
	}

}
