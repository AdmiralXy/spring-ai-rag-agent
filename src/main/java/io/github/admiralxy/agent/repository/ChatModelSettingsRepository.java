package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.ChatModelSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatModelSettingsRepository extends JpaRepository<ChatModelSettingsEntity, UUID> {

    List<ChatModelSettingsEntity> findAllByOrderByPriorityAscCreatedAtAsc();

    Optional<ChatModelSettingsEntity> findFirstByNameIgnoreCase(String name);

    Optional<ChatModelSettingsEntity> findFirstByLabelIgnoreCase(String label);
}
