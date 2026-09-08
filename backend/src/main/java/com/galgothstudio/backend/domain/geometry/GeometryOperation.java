package com.galgothstudio.backend.domain.geometry;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Whitelist cerrada de operaciones de geometría -- ticket 005, master
 * prompt §9.2. Este es el ÚNICO camino por el que cualquier escritura de
 * geometría (manual server-side o IA) pasa; no hay otra forma soportada
 * de mutar bones/cuboids de un {@link com.galgothstudio.backend.domain.model.MobProjectModel}.
 *
 * <p>El campo discriminador {@code "op"} decide el subtipo concreto. Un
 * valor de {@code op} fuera de esta lista falla la deserialización
 * Jackson (InvalidTypeIdException) antes de que exista ninguna lista de
 * operaciones que aplicar -- así se garantiza el rechazo atómico del
 * batch completo para el caso "operación fuera de whitelist" (AC #2).
 * El caso "payload inválido para una operación conocida" se valida
 * aparte, en {@link GeometryEngine}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "op")
@JsonSubTypes({
		@JsonSubTypes.Type(value = CreateBone.class, name = "createBone"),
		@JsonSubTypes.Type(value = CreateCuboid.class, name = "createCuboid"),
		@JsonSubTypes.Type(value = ResizeCuboid.class, name = "resizeCuboid"),
		@JsonSubTypes.Type(value = MoveCuboid.class, name = "moveCuboid"),
		@JsonSubTypes.Type(value = RotateCuboid.class, name = "rotateCuboid"),
		@JsonSubTypes.Type(value = SetBonePivot.class, name = "setBonePivot"),
		@JsonSubTypes.Type(value = SetBoneRotation.class, name = "setBoneRotation"),
		@JsonSubTypes.Type(value = ParentBone.class, name = "parentBone"),
		@JsonSubTypes.Type(value = RemoveCuboid.class, name = "removeCuboid"),
})
public sealed interface GeometryOperation
		permits CreateBone, CreateCuboid, ResizeCuboid, MoveCuboid, RotateCuboid, SetBonePivot, SetBoneRotation,
		ParentBone, RemoveCuboid {
}
