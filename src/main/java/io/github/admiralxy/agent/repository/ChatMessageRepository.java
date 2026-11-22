package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    List<ChatMessageEntity> findByChatIdOrderByCreatedAtAsc(UUID chatId);

    List<ChatMessageEntity> findByChatIdAndIdIn(UUID chatId, Collection<UUID> ids);

    boolean existsByChatId(UUID chatId);
}
