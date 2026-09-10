package com.galgothstudio.backend.project.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MobRepository extends JpaRepository<MobEntity, UUID> {

	/** Orden estable para "hasta 3 miniaturas" (HU-02) -- los mobs editados más recientemente primero. */
	List<MobEntity> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

	long countByProjectId(UUID projectId);

	/** Ticket 039 -- mismo criterio que `ProjectRepository`: un mob soft-deleted se trata como "no existe" en adelante. */
	List<MobEntity> findByProjectIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID projectId);

	Optional<MobEntity> findByIdAndDeletedAtIsNull(UUID id);

}
