package com.galgothstudio.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Ticket 072: prueba la lógica de validación (issuer + audiencia +
 * expiración) directamente contra {@link Jwt} construidos a mano — no
 * necesita un token real firmado ni un servidor JWKS, porque
 * {@code NimbusJwtDecoder} solo entra en juego para verificar la firma,
 * algo que este validador no toca (ver Javadoc de
 * {@link AuthCoreMcJwtDecoderConfig}).
 */
class AuthCoreMcJwtDecoderConfigTest {

    private static final String ISSUER = "https://auth-dev.64bitstudio.com";
    private static final String EXPECTED_AUDIENCE = "galgoth-studio";

    private static Jwt.Builder jwtFixture() {
        Instant now = Instant.now();
        return jwtFixture(ISSUER, now, now.plusSeconds(900));
    }

    private static Jwt.Builder jwtFixture(String issuer, Instant issuedAt, Instant expiresAt) {
        return Jwt.withTokenValue("test-token")
                .header("alg", "RS256")
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("sub", "4635300a-5049-4cd5-933d-a37b807c83b0")
                .claim("scope", List.of("openid", "profile"));
    }

    @Test
    void acceptsARealShapedTokenWithTheExpectedAudience() {
        Jwt jwt = jwtFixture().claim("aud", List.of(EXPECTED_AUDIENCE)).build();

        OAuth2TokenValidatorResult result =
                AuthCoreMcJwtDecoderConfig.validator(ISSUER).validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    void rejectsATokenWithADifferentAudience() {
        // Mismo emisor, misma firma válida en teoría -- pero para OTRO
        // cliente de auth-core-mc. Esta es la prueba de que la frontera de
        // tenant funciona, no solo que "hay un JWT" (blueprint Fig. 05).
        Jwt jwt = jwtFixture().claim("aud", List.of("acme-web-app")).build();

        OAuth2TokenValidatorResult result =
                AuthCoreMcJwtDecoderConfig.validator(ISSUER).validate(jwt);

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void rejectsATokenWithNoAudienceClaimAtAll() {
        Jwt jwt = jwtFixture().build();

        OAuth2TokenValidatorResult result =
                AuthCoreMcJwtDecoderConfig.validator(ISSUER).validate(jwt);

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void rejectsATokenFromADifferentIssuer() {
        Instant now = Instant.now();
        Jwt jwt = jwtFixture("https://some-other-authorization-server.example.com", now, now.plusSeconds(900))
                .claim("aud", List.of(EXPECTED_AUDIENCE))
                .build();

        OAuth2TokenValidatorResult result =
                AuthCoreMcJwtDecoderConfig.validator(ISSUER).validate(jwt);

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void rejectsAnExpiredToken() {
        Instant past = Instant.now().minusSeconds(3600);
        Jwt jwt = jwtFixture(ISSUER, past, past.plusSeconds(900))
                .claim("aud", List.of(EXPECTED_AUDIENCE))
                .build();

        OAuth2TokenValidatorResult result =
                AuthCoreMcJwtDecoderConfig.validator(ISSUER).validate(jwt);

        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void audienceAsAPlainStringInTheRawClaimsStillWorks() {
        // Hallazgo real (blueprint PROP-GS-AUTH-01, sección 03): el JWT
        // real de auth-core-mc trae "aud" como string suelto en el JSON,
        // no como arreglo -- Jwt.getAudience() normaliza igual, así que
        // el validador no necesita tratamiento especial.
        Jwt jwt = jwtFixture().claim("aud", EXPECTED_AUDIENCE).build();

        OAuth2TokenValidatorResult result =
                AuthCoreMcJwtDecoderConfig.validator(ISSUER).validate(jwt);

        assertThat(result.hasErrors()).isFalse();
    }
}
