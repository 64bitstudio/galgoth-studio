package com.galgothstudio.backend.project.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MobRepository extends JpaRepository<MobEntity, UUID> {
}
