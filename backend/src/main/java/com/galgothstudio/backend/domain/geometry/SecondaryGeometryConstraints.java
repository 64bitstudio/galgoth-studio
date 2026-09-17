package com.galgothstudio.backend.domain.geometry;

import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Constraints deterministas para geometría secundaria propuesta por
 * {@code SecondaryGeometryPlanner} -- ticket 099, HU-2b. Ninguna operación
 * inválida llega nunca al {@link GeometryEngine}: se filtra ANTES, para que
 * un solo elemento mal formado no tumbe el batch completo (HU-2b: "el job
 * no falla completo -- se aplican las operaciones válidas y se registra un
 * generationWarning por cada rechazo").
 *
 * <p>Reglas (todas explícitas en el ticket 099):
 * <ul>
 *   <li>Solo {@code createCuboid} -- geometría secundaria nunca crea/mueve
 *       bones ni redefine cuboides existentes; cualquier otro tipo de
 *       operación se rechaza entero.</li>
 *   <li>{@code boneId} debe referenciar un bone YA existente en la anatomía
 *       primaria (nunca un {@code tempId} de un {@code createBone} -- no
 *       existe ninguno en este batch) -- rechazar esto es, a la vez, la
 *       garantía de "sin cuboides huérfanos".</li>
 *   <li>{@code semanticPart} no vacío.</li>
 *   <li>Coordenadas finitas (sin NaN/Infinity) y dimensiones positivas.</li>
 *   <li>Tamaño máximo razonable por eje.</li>
 *   <li>Dentro del bounding box del personaje (con margen).</li>
 *   <li>Distancia razonable entre el {@code origin} del cuboid y el pivote
 *       de su bone -- ancla la geometría secundaria a la articulación a la
 *       que dice pertenecer.</li>
 * </ul>
 *
 * <p>Los valores por defecto son presupuestos deliberadamente generosos
 * para el template humanoide de 097 (segmentos de 2-12px) -- pensados para
 * dejar pasar garras/cuernos/jirones reales, no para acotar creatividad de
 * más.
 */
public final class SecondaryGeometryConstraints {

	public static final double DEFAULT_MAX_AXIS_SIZE = 16.0;
	public static final double DEFAULT_MAX_DISTANCE_FROM_PIVOT = 24.0;
	public static final double DEFAULT_BOUNDING_BOX_PADDING = 8.0;

	private SecondaryGeometryConstraints() {
	}

	public record Rejection(GeometryOperation operation, String reason) {
	}

	/**
	 * Un eje que la IA propuso más fino de lo que la densidad puede
	 * representar, y que se llevó al mínimo -- ticket 121. Se registra
	 * SIEMPRE: ajustar la geometría propuesta en silencio sería cambiarle el
	 * modelo al usuario sin decírselo.
	 */
	public record Adjustment(GeometryOperation original, GeometryOperation adjusted, String reason) {
	}

	public record ValidationResult(List<GeometryOperation> accepted, List<Rejection> rejected, List<Adjustment> adjustments) {

		/** Sobrecarga de compatibilidad anterior al ticket 121 -- sin ajustes. */
		public ValidationResult(List<GeometryOperation> accepted, List<Rejection> rejected) {
			this(accepted, rejected, List.of());
		}
	}

	/** Sobrecarga anterior al ticket 121: densidad X1, es decir el mínimo representable es 1 unidad entera. */
	public static ValidationResult validate(List<GeometryOperation> operations, MobProjectModel primaryModel) {
		return validate(operations, primaryModel, 1);
	}

	/**
	 * Ticket 121 -- ninguna operación puede proponer un eje más fino que lo
	 * que {@code texelsPerUnit} puede representar (a X4, 1/4 = 0,25
	 * unidades). Por debajo de eso, la cara no tiene representación posible
	 * en el atlas y sale degenerada a cualquier densidad: medido en el
	 * {@code done/118}, 10 ejes colapsados y 40 caras degeneradas en
	 * `Carcomido v4`, con las grietas de 0,1 unidades.
	 *
	 * <p><b>Se AJUSTA, no se rechaza</b> (decisión del PO, documentada en
	 * `pending/121`): rechazar borraría justo las piezas de identidad
	 * —grietas, garras, jirones— que son el objetivo del epic. Ajustar
	 * conserva la intención de la IA y la hace texturizable. El tradeoff es
	 * que la pieza queda un poco más gruesa de lo propuesto; por eso el
	 * ajuste se registra en {@link ValidationResult#adjustments()} y nunca
	 * ocurre en silencio.
	 *
	 * <p>Un eje de tamaño CERO o negativo se sigue rechazando: ahí no hay
	 * intención que preservar, y taparlo escondería un problema real.
	 */
	public static ValidationResult validate(List<GeometryOperation> operations, MobProjectModel primaryModel, int texelsPerUnit) {
		return validate(
				operations, primaryModel, DEFAULT_MAX_AXIS_SIZE, DEFAULT_MAX_DISTANCE_FROM_PIVOT, DEFAULT_BOUNDING_BOX_PADDING,
				texelsPerUnit);
	}

	public static ValidationResult validate(
			List<GeometryOperation> operations, MobProjectModel primaryModel,
			double maxAxisSize, double maxDistanceFromPivot, double boundingBoxPadding) {
		return validate(operations, primaryModel, maxAxisSize, maxDistanceFromPivot, boundingBoxPadding, 1);
	}

	public static ValidationResult validate(
			List<GeometryOperation> operations, MobProjectModel primaryModel,
			double maxAxisSize, double maxDistanceFromPivot, double boundingBoxPadding, int texelsPerUnit) {
		Map<String, Bone> bonesById = new HashMap<>();
		for (Bone bone : primaryModel.bones()) {
			bonesById.put(bone.id(), bone);
		}
		BoundingBox characterBox = BoundingBox.of(primaryModel).padded(boundingBoxPadding);

		List<GeometryOperation> accepted = new ArrayList<>();
		List<Rejection> rejected = new ArrayList<>();
		List<Adjustment> adjustments = new ArrayList<>();
		for (GeometryOperation op : operations) {
			if (!(op instanceof CreateCuboid cuboidOp)) {
				rejected.add(new Rejection(op, "geometría secundaria solo admite createCuboid, recibido: " + op.getClass().getSimpleName()));
				continue;
			}
			// El ajuste va ANTES de validar: un eje que se lleva al mínimo ya
			// no puede fallar por "dimensión inválida", y uno en cero se
			// rechaza igual porque `snapToTexelGrid` no lo toca.
			CreateCuboid effective = snapToTexelGrid(cuboidOp, texelsPerUnit, adjustments);
			String reason = rejectionReason(effective, bonesById, characterBox, maxAxisSize, maxDistanceFromPivot);
			if (reason != null) {
				rejected.add(new Rejection(cuboidOp, reason));
			} else {
				accepted.add(effective);
			}
		}
		return new ValidationResult(accepted, rejected, adjustments);
	}

	/**
	 * Lleva al mínimo representable los ejes que existen pero son más finos
	 * que un téxel, conservando el CENTRO de la pieza -- una grieta pegada a
	 * una superficie no puede saltar de lugar al engrosarse. Devuelve la
	 * operación original, sin copiar nada, cuando no hay nada que ajustar.
	 */
	private static CreateCuboid snapToTexelGrid(CreateCuboid op, int texelsPerUnit, List<Adjustment> adjustments) {
		double min = 1.0 / texelsPerUnit;
		double[] from = {op.from().x(), op.from().y(), op.from().z()};
		double[] to = {op.to().x(), op.to().y(), op.to().z()};
		StringBuilder ajustados = new StringBuilder();
		for (int axis = 0; axis < 3; axis++) {
			double size = to[axis] - from[axis];
			if (size <= 0 || size >= min || !Double.isFinite(size)) {
				continue;
			}
			double center = (from[axis] + to[axis]) / 2;
			from[axis] = center - min / 2;
			to[axis] = center + min / 2;
			if (ajustados.length() > 0) {
				ajustados.append(", ");
			}
			ajustados.append("eje ").append("xyz".charAt(axis)).append(": ").append(size).append(" -> ").append(min);
		}
		if (ajustados.length() == 0) {
			return op;
		}
		CreateCuboid adjusted = new CreateCuboid(
				op.tempId(), op.name(), op.boneId(), new Vec3(from[0], from[1], from[2]), new Vec3(to[0], to[1], to[2]), op.origin(),
				op.rotation(), op.semanticPart());
		adjustments.add(new Adjustment(op, adjusted, "más fino que un téxel a esta densidad (" + ajustados + ")"));
		return adjusted;
	}

	private static String rejectionReason(
			CreateCuboid op, Map<String, Bone> bonesById, BoundingBox characterBox, double maxAxisSize, double maxDistanceFromPivot) {
		if (op.semanticPart() == null || op.semanticPart().isBlank()) {
			return "semanticPart requerido y vino vacío";
		}
		Bone bone = bonesById.get(op.boneId());
		if (bone == null) {
			return "boneId '" + op.boneId() + "' no existe en la anatomía primaria (huérfano)";
		}
		if (!allFinite(op.from()) || !allFinite(op.to()) || !allFinite(op.origin()) || !allFinite(op.rotation())) {
			return "coordenadas no finitas (NaN/Infinity)";
		}
		double dx = op.to().x() - op.from().x();
		double dy = op.to().y() - op.from().y();
		double dz = op.to().z() - op.from().z();
		if (dx <= 0 || dy <= 0 || dz <= 0) {
			return "dimensiones inválidas: 'from' debe ser estrictamente menor que 'to' en los 3 ejes";
		}
		if (dx > maxAxisSize || dy > maxAxisSize || dz > maxAxisSize) {
			return "tamaño (" + dx + "x" + dy + "x" + dz + ") excede el máximo razonable de " + maxAxisSize + "px por eje";
		}
		if (!characterBox.overlaps(op.from(), op.to())) {
			return "cae completamente fuera del bounding box del personaje";
		}
		double distanceToPivot = distance(op.origin(), bone.pivot());
		if (distanceToPivot > maxDistanceFromPivot) {
			return "origin a " + distanceToPivot + "px del pivote de '" + bone.name() + "', supera el máximo razonable de " + maxDistanceFromPivot + "px";
		}
		return null;
	}

	private static boolean allFinite(Vec3 v) {
		return Double.isFinite(v.x()) && Double.isFinite(v.y()) && Double.isFinite(v.z());
	}

	private static double distance(Vec3 a, Vec3 b) {
		double dx = a.x() - b.x();
		double dy = a.y() - b.y();
		double dz = a.z() - b.z();
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	private record BoundingBox(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {

		static BoundingBox of(MobProjectModel model) {
			double minX = Double.POSITIVE_INFINITY;
			double minY = Double.POSITIVE_INFINITY;
			double minZ = Double.POSITIVE_INFINITY;
			double maxX = Double.NEGATIVE_INFINITY;
			double maxY = Double.NEGATIVE_INFINITY;
			double maxZ = Double.NEGATIVE_INFINITY;
			for (var cuboid : model.cuboids()) {
				minX = Math.min(minX, Math.min(cuboid.from().x(), cuboid.to().x()));
				maxX = Math.max(maxX, Math.max(cuboid.from().x(), cuboid.to().x()));
				minY = Math.min(minY, Math.min(cuboid.from().y(), cuboid.to().y()));
				maxY = Math.max(maxY, Math.max(cuboid.from().y(), cuboid.to().y()));
				minZ = Math.min(minZ, Math.min(cuboid.from().z(), cuboid.to().z()));
				maxZ = Math.max(maxZ, Math.max(cuboid.from().z(), cuboid.to().z()));
			}
			return new BoundingBox(minX, minY, minZ, maxX, maxY, maxZ);
		}

		BoundingBox padded(double padding) {
			return new BoundingBox(minX - padding, minY - padding, minZ - padding, maxX + padding, maxY + padding, maxZ + padding);
		}

		boolean overlaps(Vec3 from, Vec3 to) {
			return from.x() <= maxX && to.x() >= minX
					&& from.y() <= maxY && to.y() >= minY
					&& from.z() <= maxZ && to.z() >= minZ;
		}
	}
}
