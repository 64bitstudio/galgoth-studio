package com.galgothstudio.backend.domain.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * Ticket 020, hallazgo real: Spring Boot 4 cambió su ObjectMapper
 * autoconfigurado por defecto a Jackson 3 (`tools.jackson.*`) --
 * `Vec3JacksonModule`/`Vec4JacksonModule` (ticket 004) están escritos
 * contra la API clásica de Jackson 2 (`com.fasterxml.jackson.*`), que
 * dejó de autoconfigurarse como bean. Antes de este ticket nadie lo
 * notó porque ningún controlador REST existía todavía -- los tests
 * construían su propio {@code ObjectMapper} manualmente, sin pasar por
 * Spring. Se define el bean a mano aquí (en vez de migrar el dominio
 * completo a Jackson 3, fuera de alcance de este ticket -- ver Hecho
 * del ticket 020) para que CUALQUIER controlador REST (este y los futuros)
 * reciba/devuelva {@code MobProjectModel} con el mismo formato de array
 * para Vec3/Vec4 que el frontend y el JSON Schema.
 */
@Configuration
public class JacksonConfig {

	@Bean
	@Primary
	public ObjectMapper objectMapper() {
		return new ObjectMapper()
				.findAndRegisterModules()
				.registerModule(new JavaTimeModule())
				.registerModule(new Vec3JacksonModule())
				.registerModule(new Vec4JacksonModule());
	}

	/**
	 * Sin este bean explícito, Spring MVC sigue usando su propio
	 * ObjectMapper Jackson 3 autoconfigurado para (de)serializar los
	 * bodies HTTP -- el {@code @Primary} de arriba solo gana en los
	 * puntos de inyección genéricos (`@Autowired ObjectMapper`), no en el
	 * `HttpMessageConverter` que Spring Boot arma internamente. Se
	 * construye a mano para forzar que los controladores usen ESTE
	 * ObjectMapper (Vec3/Vec4 con formato de array), no el Jackson 3 por
	 * defecto que no conoce esos módulos.
	 *
	 * `MappingJackson2HttpMessageConverter` está marcado deprecated/for
	 * removal en Spring Framework 7 (parte del mismo giro hacia Jackson 3)
	 * -- es exactamente la pieza puente que se retira si el proyecto
	 * migra su dominio completo a Jackson 3 (ver Hecho del ticket 020).
	 * Se suprime la advertencia deliberadamente, no por descuido.
	 */
	@SuppressWarnings({"deprecation", "removal"})
	@Bean
	public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(ObjectMapper objectMapper) {
		return new MappingJackson2HttpMessageConverter(objectMapper);
	}

}
