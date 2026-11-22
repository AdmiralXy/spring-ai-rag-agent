package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.domain.ChatAttachment;
import io.github.admiralxy.agent.entity.ChatMessageEntity;
import io.github.admiralxy.agent.service.rag.TokenizerService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.content.MediaContent;
import org.springframework.ai.session.CreateSessionRequest;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatSessionMemory {

    public static final int IMAGE_TOKENS = 1600;

    private static final int MESSAGE_OVERHEAD_TOKENS = 8;
    private static final String DEFAULT_IMAGE_NAME = "image";
    private static final String SEEDED = "Seeded session of chat {} with {} messages";
    private static final EventFilter NO_HISTORY = EventFilter.builder()
            .messageTypes(Set.of(MessageType.SYSTEM))
            .build();

    private final SessionService sessionService;
    private final ChatTranscript transcript;
    private final TokenizerService tokenizerService;

    public void ensureSession(UUID chatId) {
        String sessionId = chatId.toString();
        if (sessionService.findById(sessionId) != null) {
            return;
        }
        sessionService.findOrCreate(CreateSessionRequest.builder().id(sessionId).build());
        List<SessionEvent> events = transcript.messages(chatId).stream()
                .map(ChatSessionMemory::toMessage)
                .filter(Objects::nonNull)
                .map(m -> SessionEvent.builder().sessionId(sessionId).message(m).build())
                .toList();
        if (!events.isEmpty()) {
            sessionService.appendEvents(sessionId, events, sessionService.getEventVersion(sessionId));
            log.info(SEEDED, chatId, events.size());
        }
    }

    public void rebuild(UUID chatId) {
        delete(chatId);
        ensureSession(chatId);
    }

    public void append(UUID chatId, Message message) {
        sessionService.appendMessage(chatId.toString(), message);
    }

    @Nullable
    public EventFilter historyWindow(UUID chatId, int budgetTokens) {
        List<Message> messages = activeMessages(chatId);
        if (messages.isEmpty()) {
            return null;
        }
        long used = 0;
        int fitting = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            used += estimateTokens(messages.get(i));
            if (used > budgetTokens) {
                break;
            }
            if (messages.get(i).getMessageType() == MessageType.USER) {
                fitting = messages.size() - i;
            }
        }
        if (fitting == messages.size() && used <= budgetTokens) {
            return null;
        }
        return fitting == 0 ? NO_HISTORY : EventFilter.lastN(fitting);
    }

    @Nullable
    public String lastUserText(UUID chatId) {
        List<Message> messages = activeMessages(chatId);
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message message = messages.get(i);
            if (message.getMessageType() == MessageType.USER && StringUtils.isNotBlank(message.getText())) {
                return message.getText();
            }
        }
        return null;
    }

    public List<Message> lastMessages(UUID chatId, int limit) {
        List<Message> messages = activeMessages(chatId).stream()
                .filter(m -> m.getMessageType() == MessageType.USER || m.getMessageType() == MessageType.ASSISTANT)
                .filter(m -> StringUtils.isNotBlank(m.getText()))
                .toList();
        if (limit <= 0 || messages.size() <= limit) {
            return messages;
        }
        return messages.subList(messages.size() - limit, messages.size());
    }

    public void delete(UUID chatId) {
        sessionService.delete(chatId.toString());
    }

    public int estimateTokens(Message message) {
        int tokens = MESSAGE_OVERHEAD_TOKENS + tokenizerService.countTokens(StringUtils.defaultString(message.getText()));
        if (message instanceof MediaContent media) {
            tokens += media.getMedia().size() * IMAGE_TOKENS;
        }
        return tokens;
    }

    public static UserMessage userMessage(String text, @Nullable List<ChatAttachment> attachments) {
        return UserMessage.builder().text(StringUtils.defaultString(text)).media(toMedia(attachments)).build();
    }

    public static List<Media> toMedia(@Nullable List<ChatAttachment> attachments) {
        if (attachments == null) {
            return List.of();
        }
        return attachments.stream()
                .map(a -> Media.builder()
                        .mimeType(MimeType.valueOf(a.mimeType()))
                        .data(Base64.getDecoder().decode(a.data()))
                        .name(StringUtils.defaultIfBlank(a.name(), DEFAULT_IMAGE_NAME))
                        .build())
                .toList();
    }

    private List<Message> activeMessages(UUID chatId) {
        return sessionService.getActiveMessages(chatId.toString());
    }

    @Nullable
    private static Message toMessage(ChatMessageEntity entity) {
        boolean hasImages = entity.getAttachments() != null && !entity.getAttachments().isEmpty();
        if (StringUtils.isBlank(entity.getContent()) && !hasImages) {
            return null;
        }
        return switch (entity.getRole()) {
            case USER -> userMessage(entity.getContent(), entity.getAttachments());
            case ASSISTANT -> StringUtils.isBlank(entity.getContent()) ? null
                    : new AssistantMessage(entity.getContent());
        };
    }
}
