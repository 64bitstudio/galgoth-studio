package com.galgothstudio.backend.project.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferenceImageRepository extends JpaRepository<ReferenceImageEntity, UUID> {

	/** Orden de subida (más antigua primero) -- HU-10 sube una a la vez, este orden es el más predecible para el wizard. */
	List<ReferenceImageEntity> findByMobIdOrderByCreatedAtAsc(UUID mobId);

	Optional<ReferenceImageEntity> findByIdAndMobId(UUID id, UUID mobId);

}
