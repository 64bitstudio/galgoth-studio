package com.galgothstudio.backend.internal;

import com.galgothstudio.backend.account.UserProfileService;
import com.galgothstudio.backend.project.ProjectService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquesta la purga de datos de producto al eliminar una cuenta (ticket
 * 091, mitad de HU-10 del lado de galgoth-studio) -- llamado
 * SÍNCRONAMENTE por auth-core-mc (ticket 064) antes de confirmar la baja
 * del usuario: si esta llamada falla, esa cuenta NO se elimina (decisión
 * de Marco, docs/definiciones/perfil-de-usuario.md -- "se eliminan junto
 * con la cuenta").
 */
@Service
public class PurgeAccountDataService {

	private final ProjectService projectService;
	private final UserProfileService userProfileService;

	public PurgeAccountDataService(ProjectService projectService, UserProfileService userProfileService) {
		this.projectService = projectService;
		this.userProfileService = userProfileService;
	}

	@Transactional
	public void purge(UUID userId) {
		projectService.purgeAllForOwner(userId.toString());
		userProfileService.deleteProfile(userId);
	}

}
