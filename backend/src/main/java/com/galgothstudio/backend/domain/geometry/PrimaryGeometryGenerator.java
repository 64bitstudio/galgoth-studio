package com.galgothstudio.backend.domain.geometry;

import com.galgothstudio.backend.domain.model.ModelIntent;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.template.CanonicalTemplate;
import com.galgothstudio.backend.domain.template.ProportionAdjustmentResult;
import com.galgothstudio.backend.domain.template.ProportionEstimator;
import com.galgothstudio.backend.domain.template.TemplateBoneSpec;
import com.galgothstudio.backend.domain.template.TemplateCuboidSpec;
import java.util.ArrayList;
import java.util.List;

/**
 * Construye la anatomía primaria de un mob directamente de un
 * {@link CanonicalTemplate} ya ajustado por {@link ProportionEstimator} --
 * ticket 098, HU-2/HU-2c. Función determinista, sin ninguna llamada de red
 * ni al LLM: reutiliza el mismo {@link GeometryEngine}/whitelist de
 * {@link GeometryOperation} que ya usa el camino guiado por IA -- el
 * batch de {@code createBone}/{@code createCuboid} se construye a partir del
 * template en vez de la respuesta de un {@code StructuredReasoningProvider},
 * pero se aplica y valida exactamente igual (referencias de bone válidas,
 * dimensiones positivas, sin ciclos -- ver {@link GeometryEngine#apply}).
 *
 * <p>Los ids lógicos del template (ej. {@code "left_arm"}) se usan como
 * {@code tempId}/{@code boneId} de las operaciones -- igual que un batch de
 * IA, {@link GeometryEngine} los resuelve a UUIDs reales; nunca se persiste
 * el id lógico del template como id final.
 *
 * <p><b>No incluye</b> (alcance explícito, ver ticket 098): la integración
 * con {@code GeometryPlannerService} para que el pipeline real deje de
 * pedirle al LLM la anatomía primaria se hace en conjunto con el ticket 099
 * (que además acota el prompt/whitelist del LLM a solo geometría
 * secundaria) -- hacerlo aquí habría dejado el pipeline real en un estado
 * intermedio roto (LLM ya sin responsabilidad de primaria, pero su prompt
 * todavía sin acotar a secundaria). Este generador queda completo, probado
 * de forma aislada, y listo para que 099 lo invoque.
 *
 * <p>{@code semanticPart} de {@link TemplateCuboidSpec} todavía NO se
 * propaga al {@link com.galgothstudio.backend.domain.model.Cuboid} real --
 * ese campo no existe en el dominio persistido hasta el ticket 099
 * (que lo agrega a {@code Cuboid}/{@code CreateCuboid}/schema). Se ignora
 * aquí a propósito, no es un olvido.
 */
public final class PrimaryGeometryGenerator {

	private PrimaryGeometryGenerator() {
	}

	/**
	 * @param emptyModel modelo base sin bones/cuboids (mobId/projectId/name/
	 *                   baseType/texture/etc. ya resueltos por el caller --
	 *                   este generador no inventa esos metadatos).
	 * @param template template canónico del {@code baseType} correspondiente.
	 * @param intent salida de vision para este mob -- solo se usa para
	 *               ajustar proporciones (ver {@link ProportionEstimator}).
	 * @return el modelo resultante con la anatomía primaria aplicada, y las
	 *         advertencias de {@link ProportionEstimator} (proporciones
	 *         clampadas) -- nunca lanza si el intent trae valores fuera de
	 *         rango, los clampa y avisa.
	 */
	public static PrimaryGeometryResult generate(MobProjectModel emptyModel, CanonicalTemplate template, ModelIntent intent) {
		ProportionAdjustmentResult resolved = ProportionEstimator.adjust(template, intent);
		List<GeometryOperation> operations = toOperations(resolved);
		MobProjectModel model = GeometryEngine.apply(emptyModel, operations);
		return new PrimaryGeometryResult(model, resolved.warnings());
	}

	private static List<GeometryOperation> toOperations(ProportionAdjustmentResult resolved) {
		List<GeometryOperation> operations = new ArrayList<>();
		for (TemplateBoneSpec bone : resolved.bones()) {
			operations.add(new CreateBone(bone.id(), bone.name(), bone.parentId(), bone.pivot(), bone.rotation()));
		}
		for (TemplateCuboidSpec cuboid : resolved.cuboids()) {
			operations.add(new CreateCuboid(cuboid.id(), cuboid.name(), cuboid.boneId(), cuboid.from(), cuboid.to(), cuboid.origin(), cuboid.rotation()));
		}
		return operations;
	}
}
