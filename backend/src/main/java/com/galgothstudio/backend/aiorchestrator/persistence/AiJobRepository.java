package com.galgothstudio.backend.aiorchestrator.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiJobRepository extends JpaRepository<AiJobEntity, UUID> {
}
