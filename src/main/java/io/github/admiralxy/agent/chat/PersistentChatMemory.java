package io.github.admiralxy.agent.chat;

import io.github.admiralxy.agent.entity.ChatMessageEntity;
import io.github.admiralxy.agent.entity.ConversationEntity;
import io.github.admiralxy.agent.repository.ChatMessageRepository;
import io.github.admiralxy.agent.repository.ConversationRepository;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PersistentChatMemory implements ChatMemory {

    private final ChatMessageRepository repository;
    private final ConversationRepository conversationRepository;

    @Override
    public void add(@Nonnull String conversationId, List<Message> messages) {
        ConversationEntity conversation = conversationRepository.getReferenceById(UUID.fromString(conversationId));

        for (Message m : messages) {
            var e = new ChatMessageEntity();
            e.setConversation(conversation);
            e.setRole(m.getMessageType().name());
            e.setContent(m.getText());
            repository.save(e);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        return repository.findByConversationIdOrderByCreatedAtAsc(UUID.fromString(conversationId)).stream()
                .map(e -> switch (MessageType.valueOf(e.getRole())) {
                        case USER -> new UserMessage(e.getContent());
                        case ASSISTANT -> new AssistantMessage(e.getContent());
                        case SYSTEM, TOOL -> new SystemMessage(e.getContent());
                    })
                    .map(Message.class::cast)
                    .toList();
    }

    @Override
    public void clear(@Nonnull String conversationId) {
        repository.deleteByConversationId(UUID.fromString(conversationId));
    }
}
