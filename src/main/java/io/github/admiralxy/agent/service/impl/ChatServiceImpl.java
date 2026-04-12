package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.config.properties.ChatProperties;
import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.Chat;
import io.github.admiralxy.agent.domain.ChatMessage;
import io.github.admiralxy.agent.entity.ConversationEntity;
import io.github.admiralxy.agent.registry.ChatClientsRegistry;
import io.github.admiralxy.agent.repository.ConversationRepository;
import io.github.admiralxy.agent.service.ChatService;
import io.github.admiralxy.agent.service.RagService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Disposable;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final String CONVERSATION_NOT_FOUND = "Conversation not found";
    private static final String MODEL_NOT_FOUND = "Model '%s' not found. Switching to fallback model.";
    private static final String CONTEXT_PROMPT = "Use this additional information for answer:\n%s";
    private static final String CHAT_SUMMARIZER_FALLBACK_PROMPT = "Message: %s";
    private static final String SORT_DIRECTION_COLUMN = "createdAt";
    private static final String CHAT_MEMORY_CONVERSATION_ID_KEY = "chat_memory_conversation_id";
    private static final int MAX_CHAT_TITLE_LENGTH = 120;

    private final ConversationRepository conversationRepository;
    private final RagService ragService;
    private final ChatProperties chatProperties;
    private final RagProperties ragProperties;
    private final ChatClientsRegistry chatClientsRegistry;
    private final ChatMemory chatMemory;

    @Override
    public Page<Chat> getAll(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, SORT_DIRECTION_COLUMN));
        return conversationRepository.findAll(pageable)
                .map(chat -> new Chat(chat.getId(), chat.getTitle(), chat.getModelName(), chat.getRagSpaces()));
    }

    @Override
    public Pair<UUID, String> create(List<String> ragSpaces) {
        String title = String.valueOf(Instant.now().toEpochMilli());
        ConversationEntity conversation = new ConversationEntity();
        conversation.setTitle(title);
        conversation.setTitleGenerated(false);
        conversation.setRagSpaces(ragSpaces);
        conversationRepository.save(conversation);

        return Pair.of(conversation.getId(), title);
    }

    @Override
    public String updateModelName(UUID chatId, String modelName) {
        if (!chatClientsRegistry.contains(modelName)) {
            throw new RuntimeException(MODEL_NOT_FOUND.formatted(modelName));
        }

        var conversation = conversationRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Conversation not found: " + chatId));

        conversation.setModelName(modelName);
        conversationRepository.save(conversation);
        return modelName;
    }

    @Override
    public Flux<String> send(UUID id, String modelAlias, String text) {
        if (!chatClientsRegistry.contains(modelAlias)) {
            throw new RuntimeException(MODEL_NOT_FOUND.formatted(modelAlias));
        }

        ChatClient chatClient = chatClientsRegistry.getChatClient(modelAlias);
        var properties = chatClientsRegistry.getProperties(modelAlias);
        return Mono.fromCallable(() ->
                        conversationRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND))
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(conv -> {
                    generateChatTitleIfFirstMessage(conv, text);

                    String context = ragService.buildContext(
                            conv.getRagSpaces(), text,
                            ragProperties.getPercentage(),
                            properties.maxContextTokens() / 2,
                            ragProperties.getTopK()
                    );

                    ChatClient.ChatClientRequestSpec chatSpec = chatClient.prompt()
                            .system(getSystemPrompt(properties.systemPrompt(), context))
                            .user(text)
                            .advisors(a -> a.param(CHAT_MEMORY_CONVERSATION_ID_KEY, id));

                    Flux<String> source;
                    if (properties.streaming()) {
                        source = chatSpec.stream().content();
                    } else {
                        source = Flux.just(Objects.requireNonNull(chatSpec.call().content()));
                    }

                    final StringBuilder acc = new StringBuilder();

                    Flux<String> tapped = source
                            .doOnNext(acc::append)
                            .doOnError(e -> {
                                if (e instanceof WebClientResponseException ex) {
                                    log.error("LLM stream error for conversation {}: {} \nResponse body:\n{}",
                                            id, ex.getStatusText(), ex.getResponseBodyAsString());
                                } else {
                                    log.warn("LLM stream error for conversation {}", id, e);
                                }
                            })
                            .doFinally(ignored -> {
                                try {
                                    String assistantText = acc.toString();
                                    if (!assistantText.isEmpty() && !isDuplicateAssistant(id.toString(), assistantText)) {
                                        chatMemory.add(id.toString(), java.util.List.of(new AssistantMessage(assistantText)));
                                    }
                                } catch (Exception ex) {
                                    log.error("Persist assistant message failed for conversation {}", id, ex);
                                }
                            });

                    ConnectableFlux<String> hot = tapped.replay();
                    Disposable ignored1 = hot.subscribe();
                    Disposable ignored2 = hot.connect();

                    return hot
                            .scan(new StringBuilder(), StringBuilder::append)
                            .skip(1)
                            .map(StringBuilder::toString)
                            .onErrorResume(t -> {
                                String msg = t.getMessage();
                                boolean disconnected =
                                        t instanceof java.io.IOException
                                                || (msg != null && msg.toLowerCase().contains("broken pipe"))
                                                || (msg != null && msg.toLowerCase().contains("forcibly closed"))
                                                || (msg != null && msg.toLowerCase().contains("clientabortexception"));
                                return disconnected ? Mono.empty() : Mono.error(t);
                            })
                            .doFinally(ignored -> {
                            });
                });
    }

    private void generateChatTitleIfFirstMessage(ConversationEntity conversation, String firstMessage) {
        if (conversation.isTitleGenerated()) {
            return;
        }

        List<Message> history = chatMemory.get(conversation.getId().toString());
        if (!history.isEmpty()) {
            return;
        }

        chatClientsRegistry.getSummarizerClient().ifPresent(summarizer -> {
            try {
                String prompt = buildSummarizerPrompt(
                        chatClientsRegistry.getSummarizerSystemPrompt().orElse(null),
                        firstMessage
                );
                String generatedTitle = summarizer.prompt()
                        .user(prompt)
                        .call()
                        .content();

                String title = sanitizeTitle(generatedTitle, firstMessage);
                conversation.setTitle(title);
                conversation.setTitleGenerated(true);
                conversationRepository.save(conversation);
            } catch (Exception e) {
                log.warn("Failed to generate chat title for conversation {}", conversation.getId(), e);
            }
        });
    }

    private String buildSummarizerPrompt(@Nullable String systemPrompt, String firstMessage) {
        if (StringUtils.isBlank(systemPrompt)) {
            return CHAT_SUMMARIZER_FALLBACK_PROMPT.formatted(firstMessage);
        }

        try {
            return systemPrompt.formatted(firstMessage);
        } catch (IllegalFormatException ignored) {
            return systemPrompt + StringUtils.LF + StringUtils.LF + CHAT_SUMMARIZER_FALLBACK_PROMPT.formatted(firstMessage);
        }
    }

    private String sanitizeTitle(@Nullable String generatedTitle, String fallbackMessage) {
        String title = StringUtils.defaultIfBlank(generatedTitle, fallbackMessage).trim();
        title = Strings.CI.removeStart(title, "\"");
        title = Strings.CI.removeEnd(title, "\"");

        if (title.length() > MAX_CHAT_TITLE_LENGTH) {
            title = title.substring(0, MAX_CHAT_TITLE_LENGTH).trim();
        }
        return title;
    }

    private String getSystemPrompt(@Nullable String systemPrompt, @Nullable String context) {
        StringBuilder builder = new StringBuilder();

        if (StringUtils.isNotBlank(systemPrompt)) {
            builder.append(systemPrompt);
        }
        if (StringUtils.isNotBlank(context)) {
            builder.append(StringUtils.LF).append(StringUtils.LF).append(CONTEXT_PROMPT.formatted(context));
        }

        return builder.toString();
    }

    private boolean isDuplicateAssistant(String convId, String newText) {
        try {
            var history = getLastMessages(convId, 1);
            if (history.isEmpty()) {
                return false;
            }
            var last = history.getLast();
            return (last instanceof AssistantMessage am) && newText.equals(am.getText());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<ChatMessage> history(UUID id) {
        return getLastMessages(id.toString(), chatProperties.getHistoryLimit()).stream()
                .map(m -> new ChatMessage(m.getMessageType().name(), m.getText()))
                .toList();
    }

    private List<Message> getLastMessages(String conversationId, int lastN) {
        List<Message> history = chatMemory.get(conversationId);
        if (history.isEmpty() || lastN <= 0 || history.size() <= lastN) {
            return history;
        }
        return history.subList(history.size() - lastN, history.size());
    }

    @Override
    public void delete(UUID chatId) {
        if (!conversationRepository.existsById(chatId)) {
            throw new IllegalArgumentException(CONVERSATION_NOT_FOUND);
        }
        conversationRepository.deleteById(chatId);
        chatMemory.clear(chatId.toString());
    }
}
