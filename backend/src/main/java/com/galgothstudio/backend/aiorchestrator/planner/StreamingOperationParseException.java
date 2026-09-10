package com.galgothstudio.backend.aiorchestrator.planner;

/** Un elemento completo del array (llaves balanceadas) no deserializó como {@code GeometryOperation} válida -- ticket 038. {@link GeometryPlannerService#planStreaming} la envuelve en {@link InvalidGeometryProposalException} con el contexto del proveedor. */
public class StreamingOperationParseException extends RuntimeException {

	public StreamingOperationParseException(String message, Throwable cause) {
		super(message, cause);
	}

}
