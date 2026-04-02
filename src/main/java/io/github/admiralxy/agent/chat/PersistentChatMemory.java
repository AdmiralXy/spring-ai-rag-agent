package io.github.admiralxy.agent.chat;

import io.github.admiralxy.agent.entity.ChatMessageEntity;
import io.github.admiralxy.agent.entity.ConversationEntity;
import io.github.admiralxy.agent.repository.ChatMessageRepository;
import io.github.admiralxy.agent.repository.ConversationRepository;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PersistentChatMemory implements ChatMemory {

    private final ChatMessageRepository repository;
    private final ConversationRepository conversationRepository;

    @Override
    public void add(@Nonnull String conversationId, @NonNull List<Message> messages) {
        Optional<UUID> conversationUuid = parseConversationId(conversationId);
        if (conversationUuid.isEmpty()) {
            return;
        }

        ConversationEntity conversation = conversationRepository.getReferenceById(conversationUuid.get());

        for (Message m : messages) {
            var e = new ChatMessageEntity();
            e.setConversation(conversation);
            e.setRole(m.getMessageType().name());
            e.setContent(m.getText());
            repository.save(e);
        }
    }

    @NonNull
    @Override
    public List<Message> get(@NonNull String conversationId) {
        Optional<UUID> conversationUuid = parseConversationId(conversationId);
        return conversationUuid.map(uuid -> repository.findByConversationIdOrderByCreatedAtAsc(uuid).stream()
                .map(e -> switch (MessageType.valueOf(e.getRole())) {
                    case USER -> new UserMessage(e.getContent());
                    case ASSISTANT -> new AssistantMessage(e.getContent());
                    case SYSTEM, TOOL -> new SystemMessage(e.getContent());
                })
                .map(Message.class::cast)
                .toList()).orElse(Collections.emptyList());

    }

    @Override
    public void clear(@Nonnull String conversationId) {
        Optional<UUID> conversationUuid = parseConversationId(conversationId);
        conversationUuid.ifPresent(repository::deleteByConversationId);
    }

    private Optional<UUID> parseConversationId(String conversationId) {
        try {
            return Optional.of(UUID.fromString(conversationId));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
