package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.aiorchestrator.InvalidJobStateException;
import com.galgothstudio.backend.aiorchestrator.JobNotCompletedException;
import com.galgothstudio.backend.aiorchestrator.JobNotFoundException;
import com.galgothstudio.backend.aiorchestrator.NoReferenceImageException;
import com.galgothstudio.backend.aiorchestrator.edit.NoBaseRevisionException;
import com.galgothstudio.backend.aiorchestrator.edit.StaleEditBaseException;
import com.galgothstudio.backend.aiorchestrator.planner.InvalidGeometryProposalException;
import com.galgothstudio.backend.domain.geometry.GeometryValidationException;
import com.galgothstudio.backend.domain.uv.PaintedRegionResizeConfirmationRequiredException;
import com.galgothstudio.backend.domain.uv.UvAtlasOverflowException;
import com.galgothstudio.backend.project.InvalidProjectNameException;
import com.galgothstudio.backend.project.ProjectNotFoundException;
import com.galgothstudio.backend.project.draft.DraftNotFoundException;
import com.galgothstudio.backend.project.draft.InvalidDraftException;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
import com.galgothstudio.backend.project.export.NoSavedRevisionException;
import com.galgothstudio.backend.project.geometry.UnsupportedGeometryApplyOperationException;
import com.galgothstudio.backend.project.mob.InvalidMobRequestException;
import com.galgothstudio.backend.project.reference.InvalidReferenceImageException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Traduce las excepciones de dominio de `project`/`project.draft` a respuestas HTTP explícitas -- ninguna cae en un 500 genérico sin explicación. */
@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(MobNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleMobNotFound(MobNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse("MOB_NOT_FOUND", ex.getMessage(), null));
	}

	@ExceptionHandler(DraftNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleDraftNotFound(DraftNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse("DRAFT_NOT_FOUND", ex.getMessage(), null));
	}

	@ExceptionHandler(InvalidDraftException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidDraft(InvalidDraftException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiErrorResponse("INVALID_DRAFT", ex.getMessage(), ex.getErrors()));
	}

	@ExceptionHandler(ProjectNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleProjectNotFound(ProjectNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse("PROJECT_NOT_FOUND", ex.getMessage(), null));
	}

	@ExceptionHandler(InvalidProjectNameException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidProjectName(InvalidProjectNameException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse("INVALID_PROJECT_NAME", ex.getMessage(), null));
	}

	@ExceptionHandler(InvalidMobRequestException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidMobRequest(InvalidMobRequestException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse("INVALID_MOB_REQUEST", ex.getMessage(), null));
	}

	@ExceptionHandler(InvalidReferenceImageException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidReferenceImage(InvalidReferenceImageException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse("INVALID_REFERENCE_IMAGE", ex.getMessage(), null));
	}

	@ExceptionHandler(NoReferenceImageException.class)
	public ResponseEntity<ApiErrorResponse> handleNoReferenceImage(NoReferenceImageException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse("NO_REFERENCE_IMAGE", ex.getMessage(), null));
	}

	@ExceptionHandler(JobNotFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleJobNotFound(JobNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse("JOB_NOT_FOUND", ex.getMessage(), null));
	}

	@ExceptionHandler(InvalidJobStateException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidJobState(InvalidJobStateException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse("INVALID_JOB_STATE", ex.getMessage(), null));
	}

	@ExceptionHandler(JobNotCompletedException.class)
	public ResponseEntity<ApiErrorResponse> handleJobNotCompleted(JobNotCompletedException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse("JOB_NOT_COMPLETED", ex.getMessage(), null));
	}

	@ExceptionHandler(StaleEditBaseException.class)
	public ResponseEntity<ApiErrorResponse> handleStaleEditBase(StaleEditBaseException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiErrorResponse("STALE_EDIT_BASE", ex.getMessage(), null));
	}

	@ExceptionHandler(NoBaseRevisionException.class)
	public ResponseEntity<ApiErrorResponse> handleNoBaseRevision(NoBaseRevisionException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse("NO_BASE_REVISION", ex.getMessage(), null));
	}

	/** Ticket 031, AC #6 -- primera vez que esta excepción (028) cruza la frontera HTTP directamente (028/029 siempre la manejaban internamente, nunca dejándola llegar a un controlador). */
	@ExceptionHandler(InvalidGeometryProposalException.class)
	public ResponseEntity<ApiErrorResponse> handleInvalidGeometryProposal(InvalidGeometryProposalException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse("INVALID_EDIT_PROPOSAL", ex.getMessage(), null));
	}

	@ExceptionHandler(NoSavedRevisionException.class)
	public ResponseEntity<ApiErrorResponse> handleNoSavedRevision(NoSavedRevisionException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiErrorResponse("NO_SAVED_REVISION", ex.getMessage(), null));
	}

	/** Ticket 043 -- whitelist cerrada de {@code POST /geometry/apply}: moveCuboid/rotateCuboid/pivot/bones nunca pasan por este endpoint. */
	@ExceptionHandler(UnsupportedGeometryApplyOperationException.class)
	public ResponseEntity<ApiErrorResponse> handleUnsupportedGeometryApplyOperation(UnsupportedGeometryApplyOperationException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiErrorResponse("UNSUPPORTED_GEOMETRY_OPERATION", ex.getMessage(), null));
	}

	/**
	 * Ticket 043 -- primera vez que {@link GeometryValidationException} (005)
	 * cruza la frontera HTTP directamente (antes solo la envolvían 028/029
	 * en {@link InvalidGeometryProposalException}): un batch inválido
	 * enviado a {@code POST /geometry/apply} (referencia no resuelta,
	 * dimensión resultante &lt;= 0, etc.) responde 400 explícito en vez de
	 * un 500 genérico.
	 */
	@ExceptionHandler(GeometryValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleGeometryValidation(GeometryValidationException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(new ApiErrorResponse("INVALID_GEOMETRY_OPERATION", ex.getMessage(), ex.errors()));
	}

	/** Ticket 043 -- primera vez que {@link UvAtlasOverflowException} (006/041) cruza la frontera HTTP directamente (caso "Add sin espacio libre" del Diseño técnico §2). */
	@ExceptionHandler(UvAtlasOverflowException.class)
	public ResponseEntity<ApiErrorResponse> handleUvAtlasOverflow(UvAtlasOverflowException ex) {
		List<String> details = List.of(
				"currentWidth=" + ex.currentWidth(), "currentHeight=" + ex.currentHeight(),
				"requiredWidth=" + ex.requiredWidth(), "requiredHeight=" + ex.requiredHeight());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ApiErrorResponse("UV_ATLAS_OVERFLOW", ex.getMessage(), details));
	}

	/**
	 * Ticket 043, Diseño técnico §2 -- un resize que cambiaría el footprint
	 * de al menos una cara ya {@code PAINTED} SIN {@code confirmPaintLoss}.
	 * {@code details} lista cada cara afectada como {@code "<cuboidId>:<face>"}
	 * (mismo formato lowercase que {@code FaceName} en JSON) para que el
	 * frontend arme el modal de confirmación sin una segunda llamada.
	 */
	@ExceptionHandler(PaintedRegionResizeConfirmationRequiredException.class)
	public ResponseEntity<ApiErrorResponse> handlePaintedRegionResizeConfirmationRequired(
			PaintedRegionResizeConfirmationRequiredException ex) {
		List<String> details = ex.affectedFaces()
				.stream()
				.map(face -> face.cuboidId() + ":" + face.face().name().toLowerCase(Locale.ROOT))
				.toList();
		return ResponseEntity.status(HttpStatus.CONFLICT)
				.body(new ApiErrorResponse("PAINTED_REGION_RESIZE_CONFIRMATION_REQUIRED", ex.getMessage(), details));
	}

}
