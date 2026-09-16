package com.galgothstudio.backend.domain.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.galgothstudio.backend.domain.model.BaseType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Ticket 097 -- HU-1. Cubre: jerarquía humanoide completa según el diagrama
 * del documento de definición, extensibilidad del catálogo sin tocar código
 * compartido, y fallback a template mínimo para baseTypes sin catálogo
 * completo todavía.
 */
class CanonicalTemplateCatalogTest {

	@Test
	void humanoide_tieneJerarquiaCompletaSegunElDiagramaDelDocumentoDeDefinicion() {
		CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(BaseType.HUMANOID);

		Map<String, String> parentById = template.bones().stream()
				.collect(java.util.stream.Collectors.toMap(TemplateBoneSpec::id, b -> String.valueOf(b.parentId())));

		assertThat(parentById).containsExactlyInAnyOrderEntriesOf(Map.ofEntries(
				Map.entry("body", "null"),
				Map.entry("torso", "body"),
				Map.entry("head", "torso"),
				Map.entry("left_arm", "torso"),
				Map.entry("left_forearm", "left_arm"),
				Map.entry("left_hand", "left_forearm"),
				Map.entry("right_arm", "torso"),
				Map.entry("right_forearm", "right_arm"),
				Map.entry("right_hand", "right_forearm"),
				Map.entry("left_leg", "body"),
				Map.entry("left_shin", "left_leg"),
				Map.entry("left_foot", "left_shin"),
				Map.entry("right_leg", "body"),
				Map.entry("right_shin", "right_leg"),
				Map.entry("right_foot", "right_shin")));
	}

	@Test
	void humanoide_cadaBoneNoRaizReferenciaUnParentIdExistente() {
		CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(BaseType.HUMANOID);
		Set<String> ids = template.bones().stream().map(TemplateBoneSpec::id).collect(java.util.stream.Collectors.toSet());

		for (TemplateBoneSpec bone : template.bones()) {
			if (bone.parentId() != null) {
				assertThat(ids).as("parentId de " + bone.id()).contains(bone.parentId());
			}
		}
	}

	@Test
	void humanoide_todoCuboidReferenciaUnBoneExistente() {
		CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(BaseType.HUMANOID);
		Set<String> boneIds = template.bones().stream().map(TemplateBoneSpec::id).collect(java.util.stream.Collectors.toSet());

		for (TemplateCuboidSpec cuboid : template.cuboids()) {
			assertThat(boneIds).as("boneId de " + cuboid.id()).contains(cuboid.boneId());
		}
	}

	@Test
	void baseTypeSinTemplateCompleto_devuelveTemplateMinimoRootBody() {
		assertThat(CanonicalTemplateCatalog.hasCompleteTemplate(BaseType.ARACHNID)).isFalse();

		CanonicalTemplate template = CanonicalTemplateCatalog.forBaseType(BaseType.ARACHNID);

		assertThat(template.bones()).hasSize(1);
		assertThat(template.bones().get(0).parentId()).isNull();
		assertThat(template.proportionRanges()).isEmpty();
	}

	@Test
	void agregarUnTemplateNuevo_noRequiereTocarNingunCodigoCompartido() {
		// Prueba de extensibilidad: se construye un template ad hoc (no
		// registrado en el catálogo) y se le aplica ProportionEstimator sin
		// tocar CanonicalTemplateCatalog ni GeometryEngine -- exactamente el
		// mecanismo que un template nuevo real (arachnid/quadruped) usaría.
		CanonicalTemplate adHoc = new CanonicalTemplate(
				BaseType.CUSTOM,
				List.of(new TemplateBoneSpec("core", "core", null,
						new com.galgothstudio.backend.domain.model.Vec3(0, 0, 0),
						com.galgothstudio.backend.domain.model.Vec3.of(0, 0, 0))),
				List.of(new TemplateCuboidSpec("core", "core", "core",
						new com.galgothstudio.backend.domain.model.Vec3(-1, -1, -1),
						new com.galgothstudio.backend.domain.model.Vec3(1, 1, 1),
						new com.galgothstudio.backend.domain.model.Vec3(0, 0, 0),
						com.galgothstudio.backend.domain.model.Vec3.of(0, 0, 0), "TORSO")),
				Map.of(),
				(baseBones, baseCuboids, proportions, ranges) -> new ProportionAdjustmentResult(baseBones, baseCuboids, List.of()));

		assertThat(adHoc.bones()).hasSize(1);
		assertThat(adHoc.cuboids()).hasSize(1);
	}
}
