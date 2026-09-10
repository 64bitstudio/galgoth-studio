package com.galgothstudio.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica, contra un Postgres real (Testcontainers), los criterios de
 * aceptación de pending/003-esquema-bd-inicial-migraciones.md que se
 * pueden probar sobre el esquema ya migrado por Flyway. La
 * reversibilidad de la migración (AC #6) se prueba aparte, en
 * {@link SchemaMigrationReversibilityTest} — necesita control directo
 * del ciclo de vida de Flyway, fuera del contexto de Spring.
 *
 * <p>Cada test corre en su propia transacción, revertida al final
 * ({@code @Transactional} sobre un test de Spring hace rollback
 * automático) — los tests no interfieren entre sí ni dejan basura.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SchemaConstraintsTest {

	@Autowired
	private JdbcTemplate jdbc;

	private UUID aProject() {
		UUID id = UUID.randomUUID();
		jdbc.update("insert into projects (id, name) values (?, ?)", id, "Galgoth");
		return id;
	}

	private UUID aMob(UUID projectId) {
		UUID id = UUID.randomUUID();
		jdbc.update(
				"insert into mobs (id, project_id, name, base_type, status) values (?, ?, ?, ?, ?)",
				id, projectId, "Carcomido", "humanoid", "draft");
		return id;
	}

	/** `fk_ai_jobs_base_revision` (V1) exige que `(mob_id, base_revision_number)` resuelva a una fila real de `mob_revisions` -- necesario para probar `generate_texture`/`edit_texture` (V3), que SIEMPRE corren sobre un mob con geometría ya usable. */
	private void aMobRevision(UUID mobId, int revisionNumber) {
		jdbc.update(
				"insert into mob_revisions (id, mob_id, revision_number, model_jsonb, created_by) values (?, ?, ?, '{}'::jsonb, 'user')",
				UUID.randomUUID(), mobId, revisionNumber);
	}

	@Test
	void mobs_current_revision_number_toma_default_0_cuando_se_omite() {
		UUID projectId = aProject();
		UUID mobId = aMob(projectId);

		Integer currentRevisionNumber = jdbc.queryForObject(
				"select current_revision_number from mobs where id = ?", Integer.class, mobId);

		assertThat(currentRevisionNumber).isZero();
	}

	@Test
	void ai_jobs_generate_acepta_base_revision_y_draft_version_nulos() {
		UUID projectId = aProject();
		UUID mobId = aMob(projectId);

		jdbc.update(
				"""
				insert into ai_jobs
				  (id, mob_id, job_type, status, provider, model, prompt_version, schema_version)
				values (?, ?, 'generate', 'running', 'claude', 'claude-fable-5-1', 'v1', 'v1')
				""",
				UUID.randomUUID(), mobId);

		Integer count = jdbc.queryForObject(
				"select count(*) from ai_jobs where mob_id = ? and job_type = 'generate'",
				Integer.class, mobId);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void ai_jobs_edit_rechaza_base_revision_o_draft_version_nulos() {
		UUID projectId = aProject();
		UUID mobId = aMob(projectId);

		assertThatThrownBy(() -> jdbc.update(
				"""
				insert into ai_jobs
				  (id, mob_id, job_type, status, provider, model, prompt_version, schema_version)
				values (?, ?, 'edit', 'running', 'claude', 'claude-fable-5-1', 'v1', 'v1')
				""",
				UUID.randomUUID(), mobId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void mob_drafts_no_tiene_fila_para_un_mob_recien_creado() {
		UUID projectId = aProject();
		UUID mobId = aMob(projectId);

		Integer count = jdbc.queryForObject(
				"select count(*) from mob_drafts where mob_id = ?", Integer.class, mobId);

		assertThat(count).isZero();
	}

	@Test
	void ai_job_events_payload_jsonb_acepta_json_valido_y_null() {
		UUID projectId = aProject();
		UUID mobId = aMob(projectId);
		UUID jobId = UUID.randomUUID();
		jdbc.update(
				"""
				insert into ai_jobs
				  (id, mob_id, job_type, status, provider, model, prompt_version, schema_version)
				values (?, ?, 'generate', 'running', 'claude', 'claude-fable-5-1', 'v1', 'v1')
				""",
				jobId, mobId);

		jdbc.update(
				"""
				insert into ai_job_events (id, job_id, seq, stage, payload_jsonb)
				values (?, ?, 1, 'analysing_reference', '{"kind":"preview_operations","ops":[]}'::jsonb)
				""",
				UUID.randomUUID(), jobId);

		jdbc.update(
				"insert into ai_job_events (id, job_id, seq, stage, payload_jsonb) values (?, ?, 2, 'creating_rig', null)",
				UUID.randomUUID(), jobId);

		Integer count = jdbc.queryForObject(
				"select count(*) from ai_job_events where job_id = ?", Integer.class, jobId);
		assertThat(count).isEqualTo(2);
	}

	@Test
	void mobs_no_tiene_columna_created_by() {
		Integer count = jdbc.queryForObject(
				"""
				select count(*) from information_schema.columns
				where table_name = 'mobs' and column_name = 'created_by'
				""",
				Integer.class);

		assertThat(count).isZero();
	}

	/** Ticket 054 (V3), Diseño técnico §18 -- `generate_texture`/`edit_texture` se comportan como `edit` en cuanto a exigir `base_*` NOT NULL (ver el comentario de la migración: HALLAZGO real sobre `chk_ai_jobs_base_values_by_type`). */
	@Test
	void ai_jobs_generate_texture_acepta_base_revision_y_draft_version_no_nulos() {
		UUID projectId = aProject();
		UUID mobId = aMob(projectId);
		aMobRevision(mobId, 1);

		jdbc.update(
				"""
				insert into ai_jobs
				  (id, mob_id, job_type, status, provider, model, prompt_version, schema_version, base_revision_number, base_draft_version)
				values (?, ?, 'generate_texture', 'running', 'claude', 'claude-fable-5-1', 'v1', 'v1', 1, 1)
				""",
				UUID.randomUUID(), mobId);

		Integer count = jdbc.queryForObject(
				"select count(*) from ai_jobs where mob_id = ? and job_type = 'generate_texture'", Integer.class, mobId);
		assertThat(count).isEqualTo(1);
	}

	@Test
	void ai_jobs_edit_texture_acepta_target_bone_id_y_base_valores_no_nulos() {
		UUID projectId = aProject();
		UUID mobId = aMob(projectId);
		aMobRevision(mobId, 1);
		UUID jobId = UUID.randomUUID();

		jdbc.update(
				"""
				insert into ai_jobs
				  (id, mob_id, job_type, status, provider, model, prompt_version, schema_version, base_revision_number, base_draft_version, target_bone_id)
				values (?, ?, 'edit_texture', 'running', 'openai', 'gpt-image-2.5-sunburst-2026-09-08', 'texture-sheet-v1', 'texture-sheet-v1', 1, 1, 'bone-1')
				""",
				jobId, mobId);

		String targetBoneId = jdbc.queryForObject("select target_bone_id from ai_jobs where id = ?", String.class, jobId);
		assertThat(targetBoneId).isEqualTo("bone-1");
	}

	@Test
	void ai_jobs_generate_texture_rechaza_base_revision_o_draft_version_nulos() {
		UUID projectId = aProject();
		UUID mobId = aMob(projectId);

		assertThatThrownBy(() -> jdbc.update(
				"""
				insert into ai_jobs
				  (id, mob_id, job_type, status, provider, model, prompt_version, schema_version)
				values (?, ?, 'generate_texture', 'running', 'claude', 'claude-fable-5-1', 'v1', 'v1')
				""",
				UUID.randomUUID(), mobId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void ai_jobs_job_type_rechaza_un_valor_fuera_de_whitelist() {
		UUID projectId = aProject();
		UUID mobId = aMob(projectId);

		assertThatThrownBy(() -> jdbc.update(
				"""
				insert into ai_jobs
				  (id, mob_id, job_type, status, provider, model, prompt_version, schema_version)
				values (?, ?, 'not_a_real_job_type', 'running', 'claude', 'claude-fable-5-1', 'v1', 'v1')
				""",
				UUID.randomUUID(), mobId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void ai_jobs_target_bone_id_es_nullable() {
		UUID projectId = aProject();
		UUID mobId = aMob(projectId);
		aMobRevision(mobId, 1);
		UUID jobId = UUID.randomUUID();

		jdbc.update(
				"""
				insert into ai_jobs
				  (id, mob_id, job_type, status, provider, model, prompt_version, schema_version, base_revision_number, base_draft_version)
				values (?, ?, 'generate_texture', 'running', 'claude', 'claude-fable-5-1', 'v1', 'v1', 1, 1)
				""",
				jobId, mobId);

		String targetBoneId = jdbc.queryForObject("select target_bone_id from ai_jobs where id = ?", String.class, jobId);
		assertThat(targetBoneId).isNull();
	}

}
