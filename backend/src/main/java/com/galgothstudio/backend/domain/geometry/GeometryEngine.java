package com.galgothstudio.backend.domain.geometry;

import com.galgothstudio.backend.domain.model.Bone;
import com.galgothstudio.backend.domain.model.Cuboid;
import com.galgothstudio.backend.domain.model.CuboidFaces;
import com.galgothstudio.backend.domain.model.Face;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.model.UvLayout;
import com.galgothstudio.backend.domain.model.UvRegion;
import com.galgothstudio.backend.domain.model.Vec3;
import com.galgothstudio.backend.domain.model.Vec4;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Motor determinista de aplicación de operaciones de geometría -- ticket
 * 005, master prompt §9.2/§9.3. Único camino por el que cualquier
 * escritura de geometría (manual server-side o IA) pasa; ni el
 * exportador ni ningún controlador debe mutar bones/cuboids a mano.
 *
 * <p>{@link #apply} es una función pura: nunca muta el {@link MobProjectModel}
 * recibido (los records de dominio son inmutables y las colecciones de
 * entrada se copian antes de tocarlas) y solo construye el modelo de
 * salida si el batch COMPLETO valida sin errores -- si cualquier
 * operación falla, se lanza {@link GeometryValidationException} y el
 * caller nunca llega a ver un modelo parcialmente mutado (AC #2, batch
 * atómico).
 */
public final class GeometryEngine {

	private static final double EPSILON = 1e-9;

	private GeometryEngine() {
	}

	public static MobProjectModel apply(MobProjectModel model, List<GeometryOperation> operations) {
		LinkedHashMap<String, Bone> bones = new LinkedHashMap<>();
		for (Bone bone : model.bones()) {
			bones.put(bone.id(), bone);
		}
		LinkedHashMap<String, Cuboid> cuboids = new LinkedHashMap<>();
		for (Cuboid cuboid : model.cuboids()) {
			cuboids.put(cuboid.id(), cuboid);
		}
		List<UvRegion> regions = new ArrayList<>(model.uv().regions());
		Map<String, String> tempRefs = new HashMap<>();

		for (GeometryOperation op : operations) {
			applyOne(op, bones, cuboids, regions, tempRefs);
		}

		return new MobProjectModel(
				model.mobId(),
				model.projectId(),
				model.name(),
				model.baseType(),
				model.units(),
				new ArrayList<>(bones.values()),
				new ArrayList<>(cuboids.values()),
				model.texture(),
				new UvLayout(model.uv().textureWidth(), model.uv().textureHeight(), regions),
				model.animations(),
				model.exportSettings(),
				model.referenceImages());
	}

	private static void applyOne(
			GeometryOperation op,
			Map<String, Bone> bones,
			Map<String, Cuboid> cuboids,
			List<UvRegion> regions,
			Map<String, String> tempRefs) {
		switch (op) {
			case CreateBone c -> applyCreateBone(c, bones, tempRefs);
			case CreateCuboid c -> applyCreateCuboid(c, bones, cuboids, tempRefs);
			case ResizeCuboid c -> applyResizeCuboid(c, cuboids, tempRefs);
			case MoveCuboid c -> applyMoveCuboid(c, cuboids, tempRefs);
			case RotateCuboid c -> applyRotateCuboid(c, cuboids, tempRefs);
			case SetBonePivot c -> applySetBonePivot(c, bones, tempRefs);
			case SetBoneRotation c -> applySetBoneRotation(c, bones, tempRefs);
			case ParentBone c -> applyParentBone(c, bones, tempRefs);
			case RemoveCuboid c -> applyRemoveCuboid(c, cuboids, regions, tempRefs);
		}
	}

	// -- createBone / createCuboid --------------------------------------

	private static void applyCreateBone(CreateBone op, Map<String, Bone> bones, Map<String, String> tempRefs) {
		requireNonBlank(op.name(), "createBone.name");
		String tempId = requireTempId(op.tempId(), "createBone", tempRefs);
		String resolvedParentId = resolveOptionalBoneRef(op.parentId(), bones, tempRefs, "createBone.parentId");
		requireVec3(op.pivot(), "createBone.pivot");
		requireVec3(op.rotation(), "createBone.rotation");

		String newId = UUID.randomUUID().toString();
		bones.put(newId, new Bone(newId, op.name(), resolvedParentId, op.pivot(), op.rotation()));
		tempRefs.put(tempId, newId);
	}

	private static void applyCreateCuboid(
			CreateCuboid op, Map<String, Bone> bones, Map<String, Cuboid> cuboids, Map<String, String> tempRefs) {
		requireNonBlank(op.name(), "createCuboid.name");
		String tempId = requireTempId(op.tempId(), "createCuboid", tempRefs);
		String resolvedBoneId = resolveBoneRef(op.boneId(), bones, tempRefs, "createCuboid.boneId");
		requireVec3(op.from(), "createCuboid.from");
		requireVec3(op.to(), "createCuboid.to");
		requireVec3(op.origin(), "createCuboid.origin");
		requireVec3(op.rotation(), "createCuboid.rotation");
		validatePositiveDimensions(op.from(), op.to(), "createCuboid");

		String newId = UUID.randomUUID().toString();
		cuboids.put(
				newId,
				new Cuboid(
						newId, op.name(), resolvedBoneId, op.from(), op.to(), op.origin(), op.rotation(),
						placeholderFaces()));
		tempRefs.put(tempId, newId);
	}

	private static CuboidFaces placeholderFaces() {
		// AutoUv (ticket 006) asigna uv/texture real -- geometría no decide UV.
		Face placeholder = new Face(new Vec4(0, 0, 0, 0), null);
		return new CuboidFaces(placeholder, placeholder, placeholder, placeholder, placeholder, placeholder);
	}

	// -- resizeCuboid / moveCuboid / rotateCuboid ------------------------

	private static void applyResizeCuboid(ResizeCuboid op, Map<String, Cuboid> cuboids, Map<String, String> tempRefs) {
		String id = resolveCuboidRef(op.target(), cuboids, tempRefs, "resizeCuboid.target");
		requireVec3(op.scale(), "resizeCuboid.scale");
		if (op.scale().x() <= 0 || op.scale().y() <= 0 || op.scale().z() <= 0) {
			throw new GeometryValidationException(
					"resizeCuboid: 'scale' debe ser > 0 en los 3 ejes, recibió " + op.scale());
		}

		Cuboid current = cuboids.get(id);
		Vec3 center = midpoint(current.from(), current.to());
		Vec3 newSize = new Vec3(
				(current.to().x() - current.from().x()) * op.scale().x(),
				(current.to().y() - current.from().y()) * op.scale().y(),
				(current.to().z() - current.from().z()) * op.scale().z());
		Vec3 newFrom = new Vec3(
				center.x() - newSize.x() / 2, center.y() - newSize.y() / 2, center.z() - newSize.z() / 2);
		Vec3 newTo = new Vec3(
				center.x() + newSize.x() / 2, center.y() + newSize.y() / 2, center.z() + newSize.z() / 2);
		validatePositiveDimensions(newFrom, newTo, "resizeCuboid");

		cuboids.put(
				id,
				new Cuboid(
						current.id(), current.name(), current.boneId(), newFrom, newTo, current.origin(),
						current.rotation(), current.faces()));
	}

	private static void applyMoveCuboid(MoveCuboid op, Map<String, Cuboid> cuboids, Map<String, String> tempRefs) {
		String id = resolveCuboidRef(op.target(), cuboids, tempRefs, "moveCuboid.target");
		requireVec3(op.delta(), "moveCuboid.delta");

		Cuboid current = cuboids.get(id);
		Vec3 newFrom = addVec3(current.from(), op.delta());
		Vec3 newTo = addVec3(current.to(), op.delta());
		Vec3 newOrigin = addVec3(current.origin(), op.delta());
		// moveCuboid es traslación rígida (from+to+origin por igual, ver
		// Hecho del ticket 005) -- las dimensiones no cambian con una
		// traslación, pero se valida igual como guarda defensiva genérica
		// compartida con resizeCuboid (AC #4 nombra ambas operaciones).
		validatePositiveDimensions(newFrom, newTo, "moveCuboid");

		cuboids.put(
				id,
				new Cuboid(
						current.id(), current.name(), current.boneId(), newFrom, newTo, newOrigin,
						current.rotation(), current.faces()));
	}

	private static void applyRotateCuboid(RotateCuboid op, Map<String, Cuboid> cuboids, Map<String, String> tempRefs) {
		String id = resolveCuboidRef(op.target(), cuboids, tempRefs, "rotateCuboid.target");
		requireVec3(op.rotationDeg(), "rotateCuboid.rotationDeg");

		Cuboid current = cuboids.get(id);
		Vec3 newRotation = addVec3(current.rotation(), op.rotationDeg());
		cuboids.put(
				id,
				new Cuboid(
						current.id(), current.name(), current.boneId(), current.from(), current.to(),
						current.origin(), newRotation, current.faces()));
	}

	// -- setBonePivot / setBoneRotation / parentBone ---------------------

	private static void applySetBonePivot(SetBonePivot op, Map<String, Bone> bones, Map<String, String> tempRefs) {
		String id = resolveBoneRef(op.target(), bones, tempRefs, "setBonePivot.target");
		requireVec3(op.pivot(), "setBonePivot.pivot");

		Bone current = bones.get(id);
		bones.put(id, new Bone(current.id(), current.name(), current.parentId(), op.pivot(), current.rotation()));
	}

	private static void applySetBoneRotation(
			SetBoneRotation op, Map<String, Bone> bones, Map<String, String> tempRefs) {
		String id = resolveBoneRef(op.target(), bones, tempRefs, "setBoneRotation.target");
		requireVec3(op.rotation(), "setBoneRotation.rotation");

		Bone current = bones.get(id);
		bones.put(id, new Bone(current.id(), current.name(), current.parentId(), current.pivot(), op.rotation()));
	}

	private static void applyParentBone(ParentBone op, Map<String, Bone> bones, Map<String, String> tempRefs) {
		String id = resolveBoneRef(op.target(), bones, tempRefs, "parentBone.target");
		String newParentId = resolveOptionalBoneRef(op.newParentId(), bones, tempRefs, "parentBone.newParentId");
		if (newParentId != null) {
			if (newParentId.equals(id)) {
				throw new GeometryValidationException("parentBone: un bone no puede ser su propio padre ('" + id + "')");
			}
			if (createsCycle(id, newParentId, bones)) {
				throw new GeometryValidationException(
						"parentBone: la reasignación de '" + id + "' a padre '" + newParentId
								+ "' crearía un ciclo en la jerarquía de bones");
			}
		}

		Bone current = bones.get(id);
		bones.put(id, new Bone(current.id(), current.name(), newParentId, current.pivot(), current.rotation()));
	}

	private static boolean createsCycle(String targetId, String newParentId, Map<String, Bone> bones) {
		String cursor = newParentId;
		while (cursor != null) {
			if (cursor.equals(targetId)) {
				return true;
			}
			Bone parent = bones.get(cursor);
			cursor = parent == null ? null : parent.parentId();
		}
		return false;
	}

	// -- removeCuboid -----------------------------------------------------

	private static void applyRemoveCuboid(
			RemoveCuboid op, Map<String, Cuboid> cuboids, List<UvRegion> regions, Map<String, String> tempRefs) {
		String id = resolveCuboidRef(op.target(), cuboids, tempRefs, "removeCuboid.target");
		cuboids.remove(id);
		// Cascade: ninguna uv.region debe seguir apuntando a un cuboid que
		// ya no existe (AC #5, referencias siguen siendo válidas).
		regions.removeIf(region -> region.cuboidId().equals(id));
	}

	// -- helpers compartidos -----------------------------------------------

	private static String requireTempId(String tempId, String opLabel, Map<String, String> tempRefs) {
		if (tempId == null || tempId.isBlank()) {
			throw new GeometryValidationException(opLabel + ": 'tempId' es requerido");
		}
		if (tempRefs.containsKey(tempId)) {
			throw new GeometryValidationException(opLabel + ": tempId duplicado en el batch '" + tempId + "'");
		}
		return tempId;
	}

	private static void requireNonBlank(String value, String label) {
		if (value == null || value.isBlank()) {
			throw new GeometryValidationException(label + " no puede estar vacío");
		}
	}

	private static void requireVec3(Vec3 v, String label) {
		if (v == null) {
			throw new GeometryValidationException(label + " es requerido");
		}
	}

	private static String resolveBoneRef(String ref, Map<String, Bone> bones, Map<String, String> tempRefs, String opLabel) {
		if (ref == null) {
			throw new GeometryValidationException(opLabel + ": referencia de bone requerida y ausente");
		}
		if (bones.containsKey(ref)) {
			return ref;
		}
		String resolved = tempRefs.get(ref);
		if (resolved != null && bones.containsKey(resolved)) {
			return resolved;
		}
		throw new GeometryValidationException(opLabel + ": referencia de bone no resuelta '" + ref + "'");
	}

	private static String resolveOptionalBoneRef(
			String ref, Map<String, Bone> bones, Map<String, String> tempRefs, String opLabel) {
		if (ref == null) {
			return null;
		}
		return resolveBoneRef(ref, bones, tempRefs, opLabel);
	}

	private static String resolveCuboidRef(
			String ref, Map<String, Cuboid> cuboids, Map<String, String> tempRefs, String opLabel) {
		if (ref == null) {
			throw new GeometryValidationException(opLabel + ": referencia de cuboid requerida y ausente");
		}
		if (cuboids.containsKey(ref)) {
			return ref;
		}
		String resolved = tempRefs.get(ref);
		if (resolved != null && cuboids.containsKey(resolved)) {
			return resolved;
		}
		throw new GeometryValidationException(opLabel + ": referencia de cuboid no resuelta '" + ref + "'");
	}

	// Paquete-visible (no private): GeometryEngineTest ejercita esta guarda
	// compartida directamente para el caso moveCuboid, cuya semántica de
	// traslación rígida (confirmada con el Product Owner) hace que un
	// delta normal nunca la dispare -- ver Hecho del ticket 005.
	static void validatePositiveDimensions(Vec3 from, Vec3 to, String opLabel) {
		if (to.x() - from.x() <= EPSILON || to.y() - from.y() <= EPSILON || to.z() - from.z() <= EPSILON) {
			throw new GeometryValidationException(
					opLabel + ": las dimensiones resultantes deben ser positivas en los 3 ejes (from=" + from
							+ ", to=" + to + ")");
		}
	}

	private static Vec3 midpoint(Vec3 a, Vec3 b) {
		return new Vec3((a.x() + b.x()) / 2, (a.y() + b.y()) / 2, (a.z() + b.z()) / 2);
	}

	private static Vec3 addVec3(Vec3 a, Vec3 b) {
		return new Vec3(a.x() + b.x(), a.y() + b.y(), a.z() + b.z());
	}

}
