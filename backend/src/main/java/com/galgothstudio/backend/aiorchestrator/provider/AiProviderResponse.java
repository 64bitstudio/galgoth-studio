package com.galgothstudio.backend.aiorchestrator.provider;

/**
 * Respuesta cruda de cualquier proveedor de IA (ticket 025, master
 * prompt §20) -- `rawContent` es el JSON SIN VALIDAR devuelto por el
 * modelo (un `ModelIntent` o una propuesta de `GeometryOperation[]`,
 * según quién llame); validarlo es responsabilidad del caller (tickets
 * 028/031), no de esta capa.
 *
 * `provider`/`model` los conoce el proveedor mismo; `promptVersion`/
 * `schemaVersion` los conoce el CALLER (distintos casos de uso -- visión
 * inicial vs. edición -- usan prompts/esquemas distintos) y viajan de
 * ida y vuelta sin que el proveedor los interprete. Las 4 columnas
 * existen para trazabilidad/reproducibilidad (master prompt §20) --
 * mismos 4 campos que `ai_jobs.provider/model/prompt_version/
 * schema_version` (ticket 003), que un futuro orquestador (028+)
 * persistirá con estos valores tal cual.
 */
public record AiProviderResponse(String rawContent, String provider, String model, String promptVersion, String schemaVersion) {
}
