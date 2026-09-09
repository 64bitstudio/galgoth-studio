package com.galgothstudio.backend.domain.export.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.domain.export.BBModelExporterV5;
import com.galgothstudio.backend.domain.model.BaseType;
import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.ExportSettings;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.FormatVersion;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.TextureDocument;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import com.galgothstudio.backend.domain.uv.AlphaAutoPackStrategy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Ticket 013 -- `FmmCompatibilityValidator`. Cubre las 4 AC del ticket.
 */
class FmmCompatibilityValidatorTest {

	private static CuboidFaces emptyFaces() {
		Face placeholder = new Face(new Vec4(0, 0, 0, 0), null);
		return new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
	}

	private static MobProjectModel modelWith(List<Bone> bones, List<Cuboid> cuboids) {
		return new MobProjectModel(
				"mob-1", "project-1", "Test Mob", BaseType.HUMANOID, MobProjectModel.UNITS_MINECRAFT_PIXELS, bones,
				cuboids, new TextureDocument(64, 64, null), new UvLayout(64, 64, new ArrayList<>()), new ArrayList<>(),
				new ExportSettings(FormatVersion.V5), new ArrayList<>());
	}

	// -- AC #1: un .bbmodel generado por 010+011 pasa todos los checks aplicables --

	@Test
	void unBbmodelGeneradoPor010Y011PasaTodosLosChecksAplicablesYSeMarcaPass() {
		String boneId = UUID.randomUUID().toString();
		Bone bone = new Bone(boneId, "torso", null, new Vec3(0, 0, 0), new Vec3(0, 0, 0));
		Cuboid cuboid = new Cuboid(
				UUID.randomUUID().toString(), "body", boneId, new Vec3(-4, 0, -2), new Vec3(4, 8, 2),
				new Vec3(0, 0, 0), new Vec3(0, 0, 0), emptyFaces());
		String json = BBModelExporterV5.export(modelWith(List.of(bone), List.of(cuboid)), new AlphaAutoPackStrategy());

		ValidationResult result = FmmCompatibilityValidator.validate(json);

		assertThat(result.pass()).isTrue();
		assertThat(result.errors()).isEmpty();
	}

	@Test
	void jsonInvalidoSeReportaComoErrorEspecificoYNoPasa() {
		ValidationResult result = FmmCompatibilityValidator.validate("{ esto no es json valido");

		assertThat(result.pass()).isFalse();
		assertThat(result.errors()).hasSize(1);
		assertThat(result.errors().get(0).rule()).isEqualTo("JSON_VALID");
	}

	@Test
	void unUuidDuplicadoEntreElementsYGroupsSeReportaConElUuidExacto() {
		String sharedId = "same-uuid";
		String json = """
				{
				  "meta": {"format_version":"5.0"},
				  "resolution": {"width":64,"height":64},
				  "elements": [{"uuid":"%s","name":"a","from":[0,0,0],"to":[1,1,1],"faces":{}}],
				  "groups": [{"uuid":"%s","name":"bone"}],
				  "outliner": [],
				  "textures": []
				}
				""".formatted(sharedId, sharedId);

		ValidationResult result = FmmCompatibilityValidator.validate(json);

		assertThat(result.pass()).isFalse();
		assertThat(result.errors()).anySatisfy(issue -> {
			assertThat(issue.rule()).isEqualTo("UUID_UNIQUE");
			assertThat(issue.element()).isEqualTo(sharedId);
		});
	}

	@Test
	void unaReferenciaDeOutlinerAUnUuidInexistenteSeReportaConEseUuid() {
		String json = """
				{
				  "meta": {"format_version":"5.0"},
				  "resolution": {"width":64,"height":64},
				  "elements": [],
				  "groups": [],
				  "outliner": ["no-existe"],
				  "textures": []
				}
				""";

		ValidationResult result = FmmCompatibilityValidator.validate(json);

		assertThat(result.pass()).isFalse();
		assertThat(result.errors()).anySatisfy(issue -> {
			assertThat(issue.rule()).isEqualTo("OUTLINER_REFERENCE");
			assertThat(issue.element()).isEqualTo("no-existe");
		});
	}

	@Test
	void unCuboidConDimensionCeroSeReportaConSuUuidYElEjeExacto() {
		String json = """
				{
				  "meta": {"format_version":"5.0"},
				  "resolution": {"width":64,"height":64},
				  "elements": [{"uuid":"flat-cube","name":"flat","from":[0,0,0],"to":[0,4,4],"faces":{
				    "north":{"uv":[0,0,4,4],"texture":null},"south":{"uv":[0,0,4,4],"texture":null},
				    "east":{"uv":[0,0,4,4],"texture":null},"west":{"uv":[0,0,4,4],"texture":null},
				    "up":{"uv":[0,0,4,4],"texture":null},"down":{"uv":[0,0,4,4],"texture":null}}}],
				  "groups": [],
				  "outliner": ["flat-cube"],
				  "textures": []
				}
				""";

		ValidationResult result = FmmCompatibilityValidator.validate(json);

		assertThat(result.pass()).isFalse();
		assertThat(result.errors()).anySatisfy(issue -> {
			assertThat(issue.rule()).isEqualTo("CUBOID_DIMENSIONS");
			assertThat(issue.element()).isEqualTo("flat-cube");
			assertThat(issue.message()).contains("eje x");
		});
	}

	@Test
	void unaUvFueraDeLosBoundsDelAtlasSeReporta() {
		String json = """
				{
				  "meta": {"format_version":"5.0"},
				  "resolution": {"width":16,"height":16},
				  "elements": [{"uuid":"cube-1","name":"c","from":[0,0,0],"to":[1,1,1],"faces":{
				    "north":{"uv":[0,0,32,4],"texture":0},"south":{"uv":[0,0,4,4],"texture":0},
				    "east":{"uv":[0,0,4,4],"texture":0},"west":{"uv":[0,0,4,4],"texture":0},
				    "up":{"uv":[0,0,4,4],"texture":0},"down":{"uv":[0,0,4,4],"texture":0}}}],
				  "groups": [],
				  "outliner": ["cube-1"],
				  "textures": [{"uuid":"t1","width":16,"height":16,"source":"data:image/png;base64,x"}]
				}
				""";

		ValidationResult result = FmmCompatibilityValidator.validate(json);

		assertThat(result.pass()).isFalse();
		assertThat(result.errors()).anySatisfy(issue -> assertThat(issue.rule()).isEqualTo("FACE_UV_BOUNDS"));
	}

	@Test
	void unIndiceDeTexturaInexistenteSeReporta() {
		String json = """
				{
				  "meta": {"format_version":"5.0"},
				  "resolution": {"width":16,"height":16},
				  "elements": [{"uuid":"cube-1","name":"c","from":[0,0,0],"to":[1,1,1],"faces":{
				    "north":{"uv":[0,0,4,4],"texture":5},"south":{"uv":[0,0,4,4],"texture":null},
				    "east":{"uv":[0,0,4,4],"texture":null},"west":{"uv":[0,0,4,4],"texture":null},
				    "up":{"uv":[0,0,4,4],"texture":null},"down":{"uv":[0,0,4,4],"texture":null}}}],
				  "groups": [],
				  "outliner": ["cube-1"],
				  "textures": []
				}
				""";

		ValidationResult result = FmmCompatibilityValidator.validate(json);

		assertThat(result.pass()).isFalse();
		assertThat(result.errors()).anySatisfy(issue -> assertThat(issue.rule()).isEqualTo("TEXTURE_INDEX"));
	}

	// -- AC #2: bone con nombre especial sigue la convención esperada --------

	@Test
	void unBoneLlamadoHitboxConCuboidsHijosReportaWarningDeGeometriaPerdida() {
		String json = """
				{
				  "meta": {"format_version":"5.0"},
				  "resolution": {"width":16,"height":16},
				  "elements": [{"uuid":"cube-1","name":"c","from":[0,0,0],"to":[1,1,1],"faces":{
				    "north":{"uv":[0,0,4,4],"texture":null},"south":{"uv":[0,0,4,4],"texture":null},
				    "east":{"uv":[0,0,4,4],"texture":null},"west":{"uv":[0,0,4,4],"texture":null},
				    "up":{"uv":[0,0,4,4],"texture":null},"down":{"uv":[0,0,4,4],"texture":null}}}],
				  "groups": [{"uuid":"bone-1","name":"hitbox"}],
				  "outliner": [{"uuid":"bone-1","isOpen":true,"children":["cube-1"]}],
				  "textures": []
				}
				""";

		ValidationResult result = FmmCompatibilityValidator.validate(json);

		// Es una advertencia (el archivo sigue siendo válido para abrir), no bloquea el PASS.
		assertThat(result.pass()).isTrue();
		assertThat(result.warnings()).anySatisfy(issue -> {
			assertThat(issue.rule()).isEqualTo("SPECIAL_BONE_GEOMETRY_DROPPED");
			assertThat(issue.element()).isEqualTo("bone-1");
		});
	}

	@Test
	void unBoneLlamadoHitboxSinCuboidsHijosNoDisparaNingunHallazgo() {
		String json = """
				{
				  "meta": {"format_version":"5.0"},
				  "resolution": {"width":16,"height":16},
				  "elements": [],
				  "groups": [{"uuid":"bone-1","name":"hitbox"}],
				  "outliner": [{"uuid":"bone-1","isOpen":true,"children":[]}],
				  "textures": []
				}
				""";

		ValidationResult result = FmmCompatibilityValidator.validate(json);

		assertThat(result.pass()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

	// -- AC #4: modelo que cumple todos los checks se marca PASS explícito ---

	@Test
	void unModeloSinNingunProblemaSeMarcaPassSinIssues() {
		String json = """
				{
				  "meta": {"format_version":"5.0"},
				  "resolution": {"width":16,"height":16},
				  "elements": [],
				  "groups": [],
				  "outliner": [],
				  "textures": []
				}
				""";

		ValidationResult result = FmmCompatibilityValidator.validate(json);

		assertThat(result.pass()).isTrue();
		assertThat(result.issues()).isEmpty();
	}

}
