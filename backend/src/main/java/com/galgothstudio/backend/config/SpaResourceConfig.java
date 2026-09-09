package com.galgothstudio.backend.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Ticket 035: en despliegue real, este backend sirve la SPA de Vue
 * (`frontend/dist`, copiado a `src/main/resources/static/` antes del
 * build de la imagen -- ver `backend/Dockerfile`) desde el mismo origen
 * que la API. `router.ts` usa `createWebHistory()` (rutas reales, no
 * hash) -- sin esto, refrescar/entrar directo a una ruta profunda (ej.
 * `/projects/x/mobs/y/edit`) da 404: Spring Boot solo sirve archivos que
 * existen literalmente bajo `static/`, nunca reescribe una ruta
 * desconocida a `index.html` por sí solo.
 *
 * Hallazgo real (verificado en vivo con `docker run` antes de este
 * fix): un primer intento vía un `@Controller`/`@RequestMapping` con una
 * expresión regular que excluía el primer segmento de la ruta si
 * contenía un punto (para no capturar `/assets/*.js`/`favicon.svg`)
 * estaba mal diseñado -- el punto está en el ÚLTIMO segmento (el nombre
 * de archivo, ej. `index-abc123.js`), no en el primero (`assets`), así
 * que ese regex SÍ capturaba los assets reales y los reemplazaba por
 * `index.html` (confirmado: `curl` a un asset real devolvía
 * `text/html`, no el JS real -- la app entera quedaba rota, sin poder
 * cargar su propio bundle).
 *
 * Fix real: en vez de adivinar por convención de nombres, un
 * `PathResourceResolver` personalizado pregunta "¿existe este archivo de
 * verdad?" -- si sí, lo sirve tal cual (cualquier profundidad, cualquier
 * nombre); si no, y la ruta no es `api/**`/`actuator/**` (nunca deberían
 * llegar aquí de todas formas: los controllers anotados y Actuator
 * tienen mayor precedencia que este resource handler registrado en
 * "/**" -- este chequeo es una segunda salvaguarda, no la primera línea
 * de defensa), sirve `index.html` y deja que Vue Router resuelva la ruta
 * en el cliente.
 *
 * En dev local (`static/` vacío, solo `.gitkeep`) este handler sigue
 * registrado pero es inofensivo: nadie navega al backend directo, el
 * frontend corre en su propio servidor de Vite (puerto 5173).
 */
@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/**")
				.addResourceLocations("classpath:/static/")
				.resourceChain(true)
				.addResolver(new PathResourceResolver() {
					@Override
					protected Resource getResource(String resourcePath, Resource location) throws IOException {
						Resource requested = location.createRelative(resourcePath);
						if (requested.exists() && requested.isReadable()) {
							return requested;
						}
						if (resourcePath.startsWith("api/") || resourcePath.startsWith("actuator/")) {
							return null;
						}
						return new ClassPathResource("/static/index.html");
					}
				});
	}

}
