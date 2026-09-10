package com.galgothstudio.backend.aiorchestrator.texture;

import com.galgothstudio.backend.domain.model.MobProjectModel;
import java.util.List;

/**
 * Forma persistida en {@code ai_jobs.proposal_jsonb} para
 * {@code job_type} `generate_texture`/`edit_texture` (ticket 054) --
 * equivalente al {@code MobProjectModel} crudo que geometría persiste
 * ahí (028/031), pero la textura necesita 2 piezas más que un
 * {@code MobProjectModel} solo no puede expresar:
 *
 * <ul>
 * <li>{@code model.texture().storageKey()} de este wrapper es
 * DELIBERADAMENTE {@code null} -- el backend recién calcula el
 * `storageKey` real (SHA-256 sobre el bitmap canónico, autoridad
 * exclusiva de {@code TextureService}, ticket 045) en el momento del
 * Apply, nunca antes (Diseño técnico §10: "sube el bitmap a MinIO
 * PRIMERO... antes de que arranque" la transacción de Apply -- si se
 * subiera especulativamente durante el pipeline, un Reject dejaría un
 * blob huérfano ANTES de que el usuario decidiera nada; se prefiere
 * subir recién cuando el usuario ya decidió Apply).</li>
 * <li>{@code composedAtlasPngBase64} -- el bitmap PNG compuesto
 * completo (todas las caras tocadas, ya compuestas sobre una copia del
 * atlas vigente por {@code TextureCompositorService}, 053), en memoria
 * hasta que Apply decida subirlo. Base64 porque {@code jsonb} no admite
 * bytes crudos.</li>
 * </ul>
 *
 * @param model               modelo final propuesto (UV regions ya actualizadas a
 *                            {@code PAINTED}/{@code AI} para las caras tocadas), con
 *                            {@code texture().storageKey()=null} hasta el Apply.
 * @param composedAtlasPngBase64 bitmap PNG completo compuesto, pendiente de subir a MinIO.
 * @param wholeModel          {@code true} si fue HU-36 (todos los bones), {@code false} si HU-37 (un bone puntual).
 * @param touchedBoneIds      ids de los bones efectivamente regenerados.
 * @param touchedFaces        diff Antes/Después a nivel de cara (HU-38), incluye el flag de sobrescritura de pintado a mano (HU-37 AC #2).
 * @param beforeAtlasPngBase64 bitmap del atlas ANTES de esta generación -- lado "Antes" del diff (HU-38).
 */
public record TextureGenerationProposal(
		MobProjectModel model,
		String composedAtlasPngBase64,
		boolean wholeModel,
		List<String> touchedBoneIds,
		List<TouchedFace> touchedFaces,
		String beforeAtlasPngBase64) {
}
