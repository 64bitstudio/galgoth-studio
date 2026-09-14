package com.galgothstudio.backend.project.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

	/**
	 * Ticket 084 -- "Mis proyectos" (HU-02) filtra por dueño real. El
	 * método global sin filtrar (`findByDeletedAtIsNullOrderByUpdatedAtDesc`,
	 * usado hasta este ticket) se retira a propósito, mismo criterio que
	 * `MobRepository` aplicó en el ticket 039: no queda como alternativa
	 * "sin filtro" que alguien use por error más adelante. La consulta de
	 * Explorar (proyectos `PUBLIC`, ticket 086) es un método nuevo aparte,
	 * no una reutilización de este.
	 */
	List<ProjectEntity> findByOwnerRefAndDeletedAtIsNullOrderByUpdatedAtDesc(String ownerRef);

	Optional<ProjectEntity> findByIdAndDeletedAtIsNull(UUID id);

}
