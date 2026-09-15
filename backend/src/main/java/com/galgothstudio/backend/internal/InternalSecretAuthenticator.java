package com.galgothstudio.backend.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Autenticación servidor-a-servidor para {@code /api/internal/**} (ticket
 * 091) -- deliberadamente NO es JWT: el caller es auth-core-mc (ticket 064
 * de ese repo), no un navegador con la sesión de un usuario. Un secreto
 * compartido simple, comparado en tiempo constante ({@link MessageDigest#isEqual})
 * para no filtrar por temporización cuánto del secreto coincidió -- mismo
 * nivel de cuidado que el resto del proyecto le da a comparaciones de
 * credenciales (ver `TokenHasher` en auth-core-mc).
 */
@Component
public class InternalSecretAuthenticator {

	private final byte[] expectedSecret;

	public InternalSecretAuthenticator(@Value("${galgoth.internal.secret}") String expectedSecret) {
		this.expectedSecret = expectedSecret.getBytes(StandardCharsets.UTF_8);
	}

	public void require(String providedSecret) {
		if (providedSecret == null
				|| !MessageDigest.isEqual(providedSecret.getBytes(StandardCharsets.UTF_8), expectedSecret)) {
			throw new InvalidInternalSecretException();
		}
	}

}
