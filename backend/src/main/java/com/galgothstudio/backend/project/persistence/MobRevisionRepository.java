package com.galgothstudio.backend.project.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MobRevisionRepository extends JpaRepository<MobRevisionEntity, UUID> {

	Optional<MobRevisionEntity> findByMobIdAndRevisionNumber(UUID mobId, int revisionNumber);

	/** Orden ascendente -- necesario para reproducir la copia profunda de un mob en el mismo orden (ticket 021, Duplicate). */
	List<MobRevisionEntity> findByMobIdOrderByRevisionNumberAsc(UUID mobId);

}
