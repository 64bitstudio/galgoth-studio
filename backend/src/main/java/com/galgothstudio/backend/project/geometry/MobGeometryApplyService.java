package com.galgothstudio.backend.project.geometry;

import com.galgothstudio.backend.domain.geometry.CreateCuboid;
import com.galgothstudio.backend.domain.geometry.GeometryEngine;
import com.galgothstudio.backend.domain.geometry.GeometryOperation;
import com.galgothstudio.backend.domain.geometry.RemoveCuboid;
import com.galgothstudio.backend.domain.geometry.ResizeCuboid;
import com.galgothstudio.backend.domain.model.MobProjectModel;
import com.galgothstudio.backend.domain.uv.UvLayoutStrategy;
import com.galgothstudio.backend.project.draft.AutosaveResponse;
import com.galgothstudio.backend.project.draft.DraftPersistenceService;
import com.galgothstudio.backend.project.draft.DraftView;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Autoridad de negocio de {@code POST /api/mobs/{mobId}/geometry/apply}
 * (ticket 043, Diseño técnico §2/§15 de
 * `docs/definiciones/galgoth-studio-fase3-textura.md`) -- cierra el
 * Hallazgo B: el editor manual calcula geometría/UV client-side sin que el
 * backend lo revalide. Este servicio es la vía manual real que el punto 2
 * del diseño técnico describe (la vía IA es {@code AiGeometryEditPlannerService},
 * sin cambios en este ticket).
 *
 * <p>Mismo patrón de separación que {@code project/draft}: este paquete
 * NO reimplementa persistencia de draft -- reutiliza
 * {@link DraftPersistenceService#getDraft}/{@link DraftPersistenceService#autosave}
 * tal cual (mismo mecanismo de {@code mob_drafts}/{@code draft_version} que
 * ya usan {@code GET}/{@code PATCH /draft}), así que un mob sin draft
 * todavía responde el mismo 404 ({@code DRAFT_NOT_FOUND}) que esos
 * endpoints, sin lógica nueva que mantener en paralelo.
 */
@Service
public class MobGeometryApplyService {

	/**
	 * Whitelist cerrada de este endpoint -- SOLO las 3 operaciones que
	 * afectan UV (Diseño técnico §2). {@code moveCuboid}/{@code rotateCuboid}/
	 * pivot/bones nunca llegan hasta {@link GeometryEngine}: se rechazan
	 * acá, antes de tocar el draft.
	 */
	private static final Set<Class<? extends GeometryOperation>> ALLOWED_OPERATIONS =
			Set.of(CreateCuboid.class, ResizeCuboid.class, RemoveCuboid.class);

	private final DraftPersistenceService draftPersistenceService;
	private final UvLayoutStrategy uvLayoutStrategy;

	public MobGeometryApplyService(DraftPersistenceService draftPersistenceService, UvLayoutStrategy uvLayoutStrategy) {
		this.draftPersistenceService = draftPersistenceService;
		this.uvLayoutStrategy = uvLayoutStrategy;
	}

	public MobGeometryApplyResponse apply(UUID mobId, MobGeometryApplyRequest request) {
		requireWhitelistedOperations(request.operations());

		DraftView currentDraft = draftPersistenceService.getDraft(mobId);
		MobProjectModel updated = GeometryEngine.apply(
				currentDraft.model(), request.operations(), uvLayoutStrategy, request.confirmPaintLoss());

		AutosaveResponse saved = draftPersistenceService.autosave(mobId, updated);
		return new MobGeometryApplyResponse(updated, saved.draftVersion());
	}

	private static void requireWhitelistedOperations(List<GeometryOperation> operations) {
		for (GeometryOperation operation : operations) {
			if (!ALLOWED_OPERATIONS.contains(operation.getClass())) {
				throw new UnsupportedGeometryApplyOperationException(operation);
			}
		}
	}

}
