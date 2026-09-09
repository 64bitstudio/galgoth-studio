package com.galgothstudio.backend.project.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

	List<ProjectEntity> findByDeletedAtIsNullOrderByUpdatedAtDesc();

	Optional<ProjectEntity> findByIdAndDeletedAtIsNull(UUID id);

}
