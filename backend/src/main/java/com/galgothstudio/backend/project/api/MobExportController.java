package com.galgothstudio.backend.project.api;

import com.galgothstudio.backend.project.export.ExportStatusView;
import com.galgothstudio.backend.project.export.ExportedFile;
import com.galgothstudio.backend.project.export.MobExportService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ticket 032 -- ver `docs/API.md`. "Guardar y exportar" NO tiene un
 * endpoint propio: el frontend orquesta `POST /api/mobs/{mobId}/revisions`
 * (020, "Guardar") seguido de `GET .../export/bbmodel` -- dos llamadas
 * secuenciales en vez de un endpoint compuesto, reutilizando el mecanismo
 * de Guardar tal cual en vez de duplicarlo.
 */
@RestController
@RequestMapping("/api/mobs/{mobId}/export")
public class MobExportController {

	private final MobExportService exportService;

	public MobExportController(MobExportService exportService) {
		this.exportService = exportService;
	}

	@GetMapping("/status")
	public ExportStatusView status(@PathVariable UUID mobId) {
		return exportService.getStatus(mobId);
	}

	@GetMapping("/bbmodel")
	public ResponseEntity<byte[]> bbmodel(@PathVariable UUID mobId) {
		ExportedFile file = exportService.exportBbmodel(mobId);
		byte[] bytes = file.content().getBytes(StandardCharsets.UTF_8);
		ContentDisposition disposition =
				ContentDisposition.attachment().filename(file.filename(), StandardCharsets.UTF_8).build();
		// application/octet-stream, no application/json -- un .bbmodel es JSON
		// por dentro, pero acá es una DESCARGA de archivo (dispara "Guardar
		// como" en el navegador, nunca intenta renderizarlo inline). Con
		// application/json, MappingJackson2HttpMessageConverter le gana al
		// ByteArrayHttpMessageConverter y codifica el byte[] como string
		// base64 dentro de un JSON -- no el archivo crudo.
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.body(bytes);
	}

}
