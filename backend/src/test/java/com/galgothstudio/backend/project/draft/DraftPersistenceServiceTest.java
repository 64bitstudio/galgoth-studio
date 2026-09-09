package com.galgothstudio.backend.project.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.galgothstudio.backend.TestcontainersConfiguration;
import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.UvLayout;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * `DraftPersistenceService.applyGenerationProposal` (ticket 030, "Usar
 * este modelo") -- el camino feliz (crea revisión+draft en la misma
 * transacción) ya se verifica de punta a punta vía HTTP en
 * `GenerationJobControllerTest`; este test cubre lo que ese camino
 * (siempre con una propuesta de IA ya válida) nunca ejercita: el
 * rechazo de un modelo inválido.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class DraftPersistenceServiceTest {

	@Autowired
	private DraftPersistenceService draftPersistenceService;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID aProjectAndMob() {
		UUID projectId = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", projectId, "Galgoth");
		UUID mobId = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				mobId, projectId, "Carcomido", "humanoid", "draft");
		return mobId;
	}

	/** `texture` es requerido por el JSON Schema (mismo truco que `MobProjectModelValidatorTest`, ticket 004: Java permite `null` en un campo de tipo objeto aunque el schema lo exija) -- produce un modelo estructuralmente inválido sin tocar JSON crudo. */
	private MobProjectModel modelWithMissingTexture(UUID mobId, UUID projectId) {
		return new MobProjectModel(
				mobId.toString(),
				projectId.toString(),
				"Carcomido",
				BaseType.HUMANOID,
				MobProjectModel.UNITS_MINECRAFT_PIXELS,
				List.of(),
				List.of(),
				null,
				new UvLayout(64, 64, List.of()),
				List.of(),
				new ExportSettings(FormatVersion.V5),
				List.of());
	}

	@Test
	void un_modelo_invalido_no_crea_ni_revision_ni_draft() {
		UUID mobId = aProjectAndMob();
		UUID projectId = UUID.fromString(jdbc.queryForObject("select project_id from mobs where id = ?", String.class, mobId));
		MobProjectModel invalidModel = modelWithMissingTexture(mobId, projectId);

		assertThatThrownBy(() -> draftPersistenceService.applyGenerationProposal(mobId, invalidModel)).isInstanceOf(InvalidDraftException.class);

		Long revisionCount = jdbc.queryForObject("select count(*) from mob_revisions where mob_id = ?", Long.class, mobId);
		assertThat(revisionCount).isZero();
		Long draftCount = jdbc.queryForObject("select count(*) from mob_drafts where mob_id = ?", Long.class, mobId);
		assertThat(draftCount).isZero();
	}

	@Test
	void un_mob_inexistente_responde_MobNotFoundException() {
		MobProjectModel model = modelWithMissingTexture(UUID.randomUUID(), UUID.randomUUID());
		UUID unknownMobId = UUID.randomUUID();

		assertThatThrownBy(() -> draftPersistenceService.applyGenerationProposal(unknownMobId, model)).isInstanceOf(MobNotFoundException.class);
	}

}
