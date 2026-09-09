package com.galgothstudio.backend.project.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MobRevisionRepository extends JpaRepository<MobRevisionEntity, UUID> {

	Optional<MobRevisionEntity> findByMobIdAndRevisionNumber(UUID mobId, int revisionNumber);

}
