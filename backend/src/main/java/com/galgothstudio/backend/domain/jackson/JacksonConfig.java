package com.galgothstudio.backend.domain.jackson;

import com.fasterxml.jackson.databind.Module;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra {@link Vec3JacksonModule} en el ObjectMapper autoconfigurado
 * de Spring Boot (JacksonAutoConfiguration recoge cualquier bean Module)
 * -- así cualquier controlador REST futuro (ticket 010+) que reciba o
 * devuelva {@code MobProjectModel} usa la misma representación [x,y,z]
 * que el frontend y el JSON Schema, sin configurarlo de nuevo.
 */
@Configuration
public class JacksonConfig {

	@Bean
	public Module vec3JacksonModule() {
		return new Vec3JacksonModule();
	}

}
