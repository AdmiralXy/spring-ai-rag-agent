package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.SpaceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SpaceRepository extends JpaRepository<SpaceEntity, UUID> {

    Page<SpaceEntity> findByUserId(UUID userId, Pageable pageable);

    Optional<SpaceEntity> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    List<SpaceEntity> findByUserIdAndIdIn(UUID userId, Collection<UUID> ids);
}
