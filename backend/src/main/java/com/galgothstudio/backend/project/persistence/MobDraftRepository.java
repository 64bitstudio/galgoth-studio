package com.galgothstudio.backend.project.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MobDraftRepository extends JpaRepository<MobDraftEntity, UUID> {
}
