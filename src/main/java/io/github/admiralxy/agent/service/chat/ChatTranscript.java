package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.domain.AgentRunStats;
import io.github.admiralxy.agent.domain.ChatAttachment;
import io.github.admiralxy.agent.domain.ChatMessage;
import io.github.admiralxy.agent.domain.ChatMessagePart;
import io.github.admiralxy.agent.domain.ChatPartType;
import io.github.admiralxy.agent.domain.ChatRole;
import io.github.admiralxy.agent.entity.ChatMessageEntity;
import io.github.admiralxy.agent.repository.ChatMessageRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatTranscript {

    private static final String SAVE_FAILURE_FAILED = "Failed to store the failed answer of chat {}";

    private final ChatMessageRepository repository;

    public UUID saveQuestion(UUID chatId, String text, List<ChatAttachment> attachments) {
        return save(chatId, ChatRole.USER, text, null, null, attachments);
    }

    public UUID saveAnswer(UUID chatId, String content, @Nullable List<ChatMessagePart> parts,
                           @Nullable AgentRunStats stats) {
        return save(chatId, ChatRole.ASSISTANT, content, parts, stats, null);
    }

    @Nullable
    public UUID saveFailure(UUID chatId, String reasoning, String partialAnswer, String error) {
        try {
            return saveAnswer(chatId, partialAnswer, answerParts(reasoning, partialAnswer, error, null), null);
        } catch (RuntimeException e) {
            log.error(SAVE_FAILURE_FAILED, chatId, e);
            return null;
        }
    }

    public List<ChatMessage> history(UUID chatId) {
        return repository.findByChatIdOrderByCreatedAtAsc(chatId).stream()
                .map(m -> new ChatMessage(m.getId(), m.getRole(), m.getContent(), m.getParts(), m.getStats(),
                        m.getAttachments(), m.getCreatedAt()))
                .toList();
    }

    public List<ChatMessageEntity> messages(UUID chatId) {
        return repository.findByChatIdOrderByCreatedAtAsc(chatId);
    }

    public boolean isEmpty(UUID chatId) {
        return !repository.existsByChatId(chatId);
    }

    public int delete(UUID chatId, Collection<UUID> messageIds) {
        List<ChatMessageEntity> messages = repository.findByChatIdAndIdIn(chatId, messageIds);
        repository.deleteAllInBatch(messages);
        return messages.size();
    }

    @Nullable
    public static List<ChatMessagePart> answerParts(String reasoning, String answer, @Nullable String error,
                                                    @Nullable String notice) {
        if (reasoning.isEmpty() && error == null && notice == null) {
            return null;
        }
        List<ChatMessagePart> parts = new ArrayList<>(4);
        addPart(parts, ChatPartType.THINKING, reasoning);
        addPart(parts, ChatPartType.TEXT, answer);
        addPart(parts, ChatPartType.ERROR, error);
        addPart(parts, ChatPartType.NOTICE, notice);
        return parts;
    }

    private static void addPart(List<ChatMessagePart> parts, ChatPartType type, @Nullable String text) {
        if (text != null && !text.isEmpty()) {
            parts.add(ChatMessagePart.text(type, text));
        }
    }

    private UUID save(UUID chatId, ChatRole role, String content, @Nullable List<ChatMessagePart> parts,
                      @Nullable AgentRunStats stats, @Nullable List<ChatAttachment> attachments) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setChatId(chatId);
        entity.setRole(role);
        entity.setContent(content);
        entity.setParts(parts);
        entity.setStats(stats);
        entity.setAttachments(attachments == null || attachments.isEmpty() ? null : attachments);
        return repository.save(entity).getId();
    }
}
