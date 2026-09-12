package com.galgothstudio.backend.project.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

	/**
	 * Ticket 071 -- "Continuar trabajando" (Inicio) necesita los mobs más
	 * recientes CRUZANDO todos los proyectos, algo que ningún método
	 * existente cubre (todos requieren `projectId`). Sin relación JPA
	 * entre `MobEntity`/`ProjectEntity` (solo el FK crudo `project_id`),
	 * así que el filtro "proyecto no eliminado" se expresa como subquery
	 * en JPQL -- excluye tanto mobs soft-deleted como mobs cuyo proyecto
	 * esté soft-deleted (un proyecto eliminado no debe "seguir apareciendo"
	 * indirectamente vía sus mobs en Inicio).
	 */
	@Query("SELECT m FROM MobEntity m WHERE m.deletedAt IS NULL "
			+ "AND m.projectId IN (SELECT p.id FROM ProjectEntity p WHERE p.deletedAt IS NULL) "
			+ "ORDER BY m.updatedAt DESC")
	List<MobEntity> findRecentAcrossProjects(Pageable pageable);

}
