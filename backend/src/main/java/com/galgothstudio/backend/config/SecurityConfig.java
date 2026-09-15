package com.galgothstudio.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Ticket 077: agrega el Resource Server de auth-core-mc (ver {@link
 * AuthCoreMcJwtDecoderConfig} para el validador de audiencia real).
 *
 * <p>Ticket 085 (docs/definiciones/proyectos-por-usuario-y-explorar.md,
 * Diseño técnico §4) -- reemplaza el {@code anyRequest().permitAll()} de
 * este ticket por reglas explícitas: toda MUTACIÓN de un proyecto o de
 * sus recursos anidados exige {@code authenticated()} (la decisión real
 * de "dueño/público/privado" la toma {@code ProjectAccessGuard} en cada
 * servicio, no Spring Security); las LECTURAS quedan {@code permitAll()}
 * a nivel de Spring -- el guard decide si el caller (autenticado o
 * anónimo) puede verlas, para que un proyecto {@code PUBLIC} sea legible
 * sin sesión. Excepción a ese criterio, ya vigente desde el ticket 084:
 * "Mis proyectos" (`GET /api/projects`) y "Continuar trabajando"
 * (`GET /api/mobs/recent`) exigen sesión aunque sean lecturas, porque
 * ambas filtran por dueño -- no tiene sentido una versión anónima de
 * "mis cosas".
 *
 * <p><b>Deliberadamente SIN cambios</b> (fuera de alcance del ticket 085,
 * ver su "No incluye"): los 3 endpoints que sirven bytes crudos de una
 * imagen -- {@code GET .../thumbnail}, {@code GET .../texture},
 * {@code GET .../references/{id}} -- siguen {@code permitAll()} SIN
 * enforcement real en su guard tampoco (ver Javadoc de
 * {@code ThumbnailService#download} para el porqué: el frontend los
 * renderiza vía {@code <img>} directo, que no puede mandar
 * {@code Authorization}); y ninguna ruta de generación por IA
 * (`/api/mobs/{mobId}/generate`, `/api/jobs/**`, edición/generación de
 * textura) -- esos controladores no son parte de los 9 que este ticket
 * enumera.
 *
 * <p><b>Security Hotspot de Sonar, revisado -- CSRF deshabilitado a
 * propósito, no un descuido:</b> esta API nunca usa autenticación basada
 * en cookies/sesión de navegador (el vector que CSRF protege) -- la
 * autenticación es siempre {@code Authorization: Bearer}, que un
 * navegador nunca adjunta automáticamente entre sitios como sí hace con
 * una cookie de sesión. Sigue sin cambios desde el ticket 077 -- este
 * ticket (085) solo agrega reglas de autorización, no toca el mecanismo
 * de autenticación.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http, JwtDecoder jwtDecoder, ApiAuthenticationEntryPoint apiAuthenticationEntryPoint) {
        // Hallazgo real de Sonar (java:S1130): esta versión de HttpSecurity.build()
        // ya no declara `throws Exception` -- ningún método de esta cadena lo hace
        // tampoco, así que declararlo aquí era una excepción nunca lanzable.
        http.csrf(csrf -> csrf.disable())
                .exceptionHandling(handling -> handling.authenticationEntryPoint(apiAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> auth
                        // Proyectos (021/084) -- crear y "Mis proyectos".
                        .requestMatchers(HttpMethod.POST, "/api/projects").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/projects").authenticated()
                        // Proyectos -- renombrar/borrar/duplicar, agregar un mob.
                        .requestMatchers(HttpMethod.PATCH, "/api/projects/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/projects/*").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/projects/*/duplicate").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/projects/*/mobs").authenticated()
                        // "Continuar trabajando" (071/084) -- filtra por dueño, exige sesión.
                        .requestMatchers(HttpMethod.GET, "/api/mobs/recent").authenticated()
                        // Mobs -- renombrar/borrar.
                        .requestMatchers(HttpMethod.PATCH, "/api/mobs/*").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/mobs/*").authenticated()
                        // Draft/Guardar (020) -- autosave y "Guardar" son mutaciones; leer el draft no.
                        .requestMatchers(HttpMethod.PATCH, "/api/mobs/*/draft").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/mobs/*/revisions").authenticated()
                        // Apply de geometría manual (043).
                        .requestMatchers(HttpMethod.POST, "/api/mobs/*/geometry/apply").authenticated()
                        // Imágenes de referencia (024) -- solo subir es mutación; listar el
                        // metadata JSON es una lectura más (permitAll, guard decide) -- el
                        // frontend igual manda el token vía authenticatedFetch para que el
                        // dueño de un proyecto privado vea las suyas.
                        .requestMatchers(HttpMethod.POST, "/api/mobs/*/references").authenticated()
                        // Textura (045) -- subir el bitmap.
                        .requestMatchers(HttpMethod.PUT, "/api/mobs/*/texture").authenticated()
                        // Thumbnail (023) -- subir.
                        .requestMatchers(HttpMethod.POST, "/api/mobs/*/thumbnail").authenticated()
                        // El resto de las rutas: lecturas (guard decide dueño/público), los 3
                        // endpoints de bytes crudos sin enforcement, y cualquier ruta fuera del alcance de este
                        // ticket (generación por IA, jobs, texture-previews).
                        .anyRequest().permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));
        return http.build();
    }
}
