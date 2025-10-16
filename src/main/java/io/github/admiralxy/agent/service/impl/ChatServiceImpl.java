package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.config.properties.ChatProperties;
import io.github.admiralxy.agent.config.properties.ModelProperties;
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
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AbstractChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
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
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final String CONVERSATION_NOT_FOUND = "Conversation not found";
    private static final String MODEL_NOT_FOUND = "Model '%s' not found. Switching to fallback model.";
    private static final String CONTEXT_PROMPT = "Use this additional information for answer:\n%s";
    private static final String SORT_DIRECTION_COLUMN = "createdAt";

    private final ConversationRepository conversationRepository;
    private final RagService ragService;
    private final ChatProperties chatProperties;
    private final RagProperties ragProperties;
    private final ChatClientsRegistry chatClientsRegistry;
    private final ChatMemory chatMemory;

    @Override
    public Page<Chat> getAll(int size) {
        Pageable page = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, SORT_DIRECTION_COLUMN));
        return conversationRepository.findAll(page)
                .map(chat -> new Chat(chat.getId(), chat.getTitle(), chat.getModelName(), chat.getRagSpace()));
    }

    @Override
    public Pair<UUID, String> create(String ragSpace) {
        String title = String.valueOf(Instant.now().toEpochMilli());
        ConversationEntity conversation = new ConversationEntity();
        conversation.setTitle(title);
        conversation.setRagSpace(ragSpace);
        conversationRepository.save(conversation);

        return Pair.of(conversation.getId(), title);
    }

    @Override
    public String updateModelName(UUID chatId, String modelName) {
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
        ModelProperties properties = chatClientsRegistry.getProperties(modelAlias);
        return Mono.fromCallable(() ->
                        conversationRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND))
                )
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(conv -> {
                    String context = ragService.buildContext(
                            conv.getRagSpace(), text,
                            ragProperties.getPercentage(),
                            properties.getMaxContextTokens() / 2,
                            ragProperties.getTopK()
                    );

                    ChatClient.ChatClientRequestSpec chatSpec = chatClient.prompt()
                            .system(getSystemPrompt(properties.getSystemPrompt(), context))
                            .user(text)
                            .advisors(a -> a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, id));

                    Flux<String> source;
                    if (properties.isStreaming()) {
                        source = chatSpec.stream().content();
                    } else {
                        source = Flux.just(chatSpec.call().content());
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
                    Disposable keeper = hot.subscribe();
                    Disposable conn = hot.connect();

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
            var history = chatMemory.get(convId, 1);
            if (history == null || history.isEmpty()) {
                return false;
            }
            var last = history.getLast();
            return (last instanceof AssistantMessage am) && newText.equals(am.getContent());
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<ChatMessage> history(UUID id) {
        return chatMemory.get(id.toString(), chatProperties.getHistoryLimit()).stream()
                .map(m -> new ChatMessage(m.getMessageType().name(), m.getContent()))
                .toList();
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
