package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.ChatMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    Page<ChatMessageEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

    void deleteByConversationId(UUID conversationId);
}
