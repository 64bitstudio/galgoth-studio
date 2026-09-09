package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.InvalidProjectNameException;
import com.galgothstudio.backend.project.ProjectNotFoundException;
import com.galgothstudio.backend.project.draft.DraftNotFoundException;
import com.galgothstudio.backend.project.draft.InvalidDraftException;
import com.galgothstudio.backend.project.draft.MobNotFoundException;
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

}
