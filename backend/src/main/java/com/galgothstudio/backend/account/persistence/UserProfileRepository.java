package com.galgothstudio.backend.account.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfileEntity, UUID> {

	/** Descarga pública del avatar (`AccountAvatarController`) -- nunca busca por `userId` real, ver docstring de {@code UserProfileEntity.publicAvatarId}. */
	Optional<UserProfileEntity> findByPublicAvatarId(UUID publicAvatarId);
}
