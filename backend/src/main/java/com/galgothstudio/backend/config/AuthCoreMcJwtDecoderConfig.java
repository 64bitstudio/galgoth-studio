package com.galgothstudio.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Ticket 077: auth-core-mc es un Authorization Server COMPARTIDO por
 * cualquier tenant/cliente, presente o futuro, firmando con la misma
 * llave — lo único que distingue "este token es para galgoth-studio" de
 * "este token es para cualquier otra app" es el claim {@code aud}
 * (verificado en vivo, blueprint {@code PROP-GS-AUTH-01} sección 05:
 * decodificando un access token real, {@code aud} viaja como string
 * suelto en el JSON, no como arreglo). Spring NO valida esto por defecto — solo
 * firma + emisor + expiración ({@link JwtValidators#createDefaultWithIssuer}).
 * Sin este validador, un token real de cualquier otro cliente de
 * auth-core-mc pasaría igual.
 *
 * <p><b>Hallazgo real, corregido antes de que rompiera algo:</b> {@code
 * JwtDecoders.fromIssuerLocation(...)} (el helper "fácil") hace un GET
 * síncrono a {@code /.well-known/openid-configuration} en el momento en
 * que se crea el bean — eso habría roto el arranque de CUALQUIER {@code
 * @SpringBootTest} de este proyecto (ninguno levanta un auth-core-mc
 * real). {@link NimbusJwtDecoder#withJwkSetUri} en cambio configura un
 * {@code RemoteJWKSet} que resuelve perezoso, solo cuando de verdad hay
 * un token que validar — nunca en el arranque, así que los tests
 * existentes (que no protegen ninguna ruta, ver {@code SecurityConfig})
 * jamás disparan esa llamada. El path del JWKS ({@code /oauth2/jwks}) ya
 * es conocido y estable (verificado en vivo contra auth-core-mc, no
 * asumido) — no hace falta descubrirlo.
 *
 * <p>{@code EXPECTED_AUDIENCE} es literal, no una property — a diferencia
 * de {@code issuer-uri} (distinto por ambiente), la audiencia esperada es
 * siempre la identidad propia de este cliente en auth-core-mc, nunca algo
 * que un operador necesite cambiar por ambiente.
 */
@Configuration
public class AuthCoreMcJwtDecoderConfig {

    private static final String EXPECTED_AUDIENCE = "galgoth-studio";
    private static final String JWKS_PATH = "/oauth2/jwks";

    @Bean
    public JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
        NimbusJwtDecoder decoder =
                NimbusJwtDecoder.withJwkSetUri(issuerUri + JWKS_PATH).build();
        decoder.setJwtValidator(validator(issuerUri));
        return decoder;
    }

    /**
     * Separado de {@link #jwtDecoder} para poder probar la lógica de
     * validación (issuer + audiencia + expiración) contra {@link Jwt}
     * construidos a mano, sin necesitar un token real firmado ni un
     * servidor JWKS — {@code NimbusJwtDecoder} solo entra en juego para
     * verificar la firma, algo que este validador no toca.
     *
     * <p><b>Hallazgo real, atrapado por el propio test de este ticket
     * antes de llegar a producción:</b> un {@code JwtClaimValidator<List<String>>}
     * (el patrón "de libro") hace un cast directo del claim crudo del
     * mapa de claims — como el JSON real trae {@code "aud"} como string
     * suelto (no arreglo), ese cast lanza {@code ClassCastException} en
     * vez de simplemente rechazar el token. {@link Jwt#getAudience()} sí
     * normaliza correctamente sin importar la forma cruda del JSON — el
     * validador de audiencia usa ese accessor, nunca el mapa de claims
     * crudo directamente. Segundo hallazgo del mismo test: {@code
     * getAudience()} devuelve {@code null} (no una lista vacía) cuando el
     * claim no existe en absoluto — el chequeo de null es necesario, no
     * defensivo de más.
     */
    static OAuth2TokenValidator<Jwt> validator(String issuerUri) {
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = jwt -> jwt.getAudience() != null
                        && jwt.getAudience().contains(EXPECTED_AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(
                        new OAuth2Error("invalid_token", "Required audience 'galgoth-studio' is missing", null));
        return new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience);
    }
}
