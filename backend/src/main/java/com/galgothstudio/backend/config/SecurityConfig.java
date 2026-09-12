package com.galgothstudio.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Ticket 072: agrega el Resource Server de auth-core-mc (ver {@link
 * AuthCoreMcJwtDecoderConfig} para el validador de audiencia real) — pero
 * NO protege ninguna ruta todavía. Decisión explícita de Marco: las 14
 * APIs existentes estaban completamente abiertas (sin este starter de
 * seguridad en el proyecto hasta este ticket) y sin ningún modelo de
 * ownership de proyectos contra el cual reconciliarlas — agregar
 * {@code anyRequest().authenticated()} habría cortado el acceso a la app
 * real sin ningún mecanismo para decidir de quién es cada proyecto, un
 * cambio que rompe compatibilidad y no algo para decidir a mitad de este
 * ticket. Este mecanismo queda listo para que un ticket futuro (una vez
 * exista ese modelo) decida qué rutas proteger.
 *
 * <p><b>Security Hotspot de Sonar, revisado — CSRF deshabilitado a
 * propósito, no un descuido:</b> esta API nunca usa autenticación basada
 * en cookies/sesión de navegador (el vector que CSRF protege) — hoy no
 * exige autenticación en absoluto, y cuando algo la exija (ticket
 * futuro) será vía {@code Authorization: Bearer}, que un navegador nunca
 * adjunta automáticamente entre sitios como sí hace con una cookie de
 * sesión. Además, no había ningún {@code SecurityFilterChain} antes de
 * este ticket, así que Spring Security nunca había podido exigir CSRF —
 * dejarlo habilitado habría roto cualquier POST/PUT/DELETE existente
 * (proyectos, generación de IA, etc.) con el primer deploy de este
 * cambio, sin que ese fuera el propósito del ticket.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) {
        // Hallazgo real de Sonar (java:S1130): esta versión de HttpSecurity.build()
        // ya no declara `throws Exception` -- ningún método de esta cadena lo hace
        // tampoco, así que declararlo aquí era una excepción nunca lanzable.
        http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));
        return http.build();
    }
}
