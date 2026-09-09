package com.galgothstudio.backend.aiorchestrator.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiJobEventRepository extends JpaRepository<AiJobEventEntity, UUID> {

	/** Reanudación vía `Last-Event-ID` (ticket 029, AC #3) -- eventos estrictamente posteriores al último visto por el cliente. */
	List<AiJobEventEntity> findByJobIdAndSeqGreaterThanOrderBySeqAsc(UUID jobId, int seq);

}
