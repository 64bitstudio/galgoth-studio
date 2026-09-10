package com.galgothstudio.backend.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.galgothstudio.backend.domain.jackson.Vec4JacksonModule;
import com.galgothstudio.backend.domain.model.FaceName;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.UvRegionStatus;
import com.galgothstudio.backend.domain.model.UvReservation;
import com.galgothstudio.backend.domain.model.UvReservationReason;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

/**
 * Ticket 040 -- contrato de datos de {@code UvRegion.status} y
 * {@code UvReservation}, AC #1/#2/#3 de
 * `in-process/040-mobprojectmodel-uvregion-uvreservation.md`. Espejo Java
 * de {@code MobProjectModelRoundTripTest}/{@code MobProjectModelSchemaTest}
 * (ticket 004), mismo mecanismo de verificación.
 */
class UvRegionUvReservationContractTest {

	private static ObjectMapper mapper() {
		return new ObjectMapper().registerModule(new Vec4JacksonModule());
	}

	/**
	 * AC #1: una revisión legacy de Fase 1+2 no tiene el campo {@code status}
	 * en su JSON almacenado -- debe deserializar con el default
	 * {@code UNPAINTED}, sin lanzar excepción y sin migración de datos.
	 */
	@Test
	void unaUvRegionLegacySinStatusDeserializaConDefaultUnpainted() throws Exception {
		String legacyJson = "{ \"cuboidId\": \"head_main\", \"face\": \"north\", \"rect\": [0, 0, 8, 8] }";

		UvRegion region = mapper().readValue(legacyJson, UvRegion.class);

		assertThat(region.status()).isEqualTo(UvRegionStatus.UNPAINTED);
		assertThat(region.cuboidId()).isEqualTo("head_main");
		assertThat(region.face()).isEqualTo(FaceName.NORTH);
		assertThat(region.rect()).isEqualTo(new Vec4(0, 0, 8, 8));
	}

	/**
	 * AC #2: una revisión legacy de Fase 1+2 no tiene el campo
	 * {@code reservations} en su JSON almacenado -- debe deserializar con
	 * el default lista vacía, sin lanzar excepción y sin migración de datos.
	 */
	@Test
	void unUvLayoutLegacySinReservationsDeserializaConListaVacia() throws Exception {
		String legacyJson = "{ \"textureWidth\": 64, \"textureHeight\": 64, \"regions\": [] }";

		UvLayout layout = mapper().readValue(legacyJson, UvLayout.class);

		assertThat(layout.reservations()).isEmpty();
		assertThat(layout.textureWidth()).isEqualTo(64);
		assertThat(layout.textureHeight()).isEqualTo(64);
	}

	/** AC #1, complemento: un JSON legacy con varias regiones sin status default TODAS a UNPAINTED. */
	@Test
	void unUvLayoutLegacyConVariasRegionesSinStatusDefaulteaTodasAUnpainted() throws Exception {
		String legacyJson = "{ \"textureWidth\": 64, \"textureHeight\": 64, \"regions\": ["
				+ "{ \"cuboidId\": \"a\", \"face\": \"north\", \"rect\": [0, 0, 8, 8] },"
				+ "{ \"cuboidId\": \"b\", \"face\": \"south\", \"rect\": [8, 0, 16, 8] }"
				+ "] }";

		UvLayout layout = mapper().readValue(legacyJson, UvLayout.class);

		assertThat(layout.regions()).extracting(UvRegion::status)
				.containsExactly(UvRegionStatus.UNPAINTED, UvRegionStatus.UNPAINTED);
		assertThat(layout.reservations()).isEmpty();
	}

	/**
	 * AC #3: round-trip de una {@code UvReservation} con
	 * {@code reason=RESIZE_ABANDONED} -- serializar y volver a deserializar
	 * produce un JSON idéntico byte a byte (mismo criterio que
	 * {@code MobProjectModelRoundTripTest}, ticket 004).
	 */
	@Test
	void unaUvReservationConReasonResizeAbandonedSobreviveElRoundTripIdentica() throws Exception {
		ObjectMapper mapper = mapper();
		UvReservation reservation = new UvReservation(
				"reservation-1", new Vec4(0, 0, 8, 8), UvReservationReason.RESIZE_ABANDONED, "head_main", FaceName.NORTH);

		String serialized = mapper.writeValueAsString(reservation);
		UvReservation deserialized = mapper.readValue(serialized, UvReservation.class);
		String roundTripped = mapper.writeValueAsString(deserialized);

		assertThat(deserialized).isEqualTo(reservation);
		JSONAssert.assertEquals(serialized, roundTripped, JSONCompareMode.STRICT);
	}

	/** AC #3, a nivel de UvLayout completo: reservations sobrevive el round-trip dentro de su contenedor. */
	@Test
	void unUvLayoutConReservationsSobreviveElRoundTripIdentico() throws Exception {
		ObjectMapper mapper = mapper();
		UvReservation reservation = new UvReservation(
				"reservation-1", new Vec4(40, 0, 48, 8), UvReservationReason.RESIZE_ABANDONED, "torso_main", FaceName.UP);
		UvLayout layout = new UvLayout(
				64, 64,
				List.of(new UvRegion("torso_main", FaceName.UP, new Vec4(0, 0, 8, 8), UvRegionStatus.PAINTED)),
				List.of(reservation));

		String serialized = mapper.writeValueAsString(layout);
		UvLayout deserialized = mapper.readValue(serialized, UvLayout.class);
		String roundTripped = mapper.writeValueAsString(deserialized);

		assertThat(deserialized).isEqualTo(layout);
		JSONAssert.assertEquals(serialized, roundTripped, JSONCompareMode.STRICT);
	}

}
