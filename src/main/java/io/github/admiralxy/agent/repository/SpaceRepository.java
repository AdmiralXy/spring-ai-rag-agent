package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.SpaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpaceRepository extends JpaRepository<SpaceEntity, UUID> {
}
