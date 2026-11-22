package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.ChatModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChatModelRepository extends JpaRepository<ChatModelEntity, UUID> {

    List<ChatModelEntity> findAllByOrderByPriorityAscCreatedAtAsc();
}
