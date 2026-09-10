package com.galgothstudio.backend.project.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MobRepository extends JpaRepository<MobEntity, UUID> {

	/**
	 * Ticket 039 -- hallazgo real de la verificación en vivo: las
	 * versiones SIN filtrar por `deletedAt` (`findByProjectIdOrderByUpdatedAtDesc`/
	 * `countByProjectId`) existieron hasta este ticket porque `mobs` no
	 * tenía soft-delete todavía -- se retiran a propósito (no quedan
	 * como alternativa "sin filtro" que alguien use por error más
	 * adelante) porque cada uno de sus 4 usos reales en `ProjectService`
	 * (conteo de "criaturas" en detalle/rename, Duplicate, dashboard)
	 * necesita excluir mobs eliminados -- sin esto, "Duplicar proyecto"
	 * copiaría también mobs ya eliminados, y el conteo/dashboard los
	 * seguiría contando.
	 */
	List<MobEntity> findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID projectId);

	long countByProjectIdAndDeletedAtIsNull(UUID projectId);

	Optional<MobEntity> findByIdAndDeletedAtIsNull(UUID id);

}
