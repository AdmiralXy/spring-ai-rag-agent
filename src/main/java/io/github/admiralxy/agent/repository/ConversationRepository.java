package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.ConversationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConversationRepository extends JpaRepository<ConversationEntity, UUID> {
}
