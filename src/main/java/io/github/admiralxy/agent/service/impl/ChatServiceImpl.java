package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.config.properties.ChatProperties;
import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.Chat;
import io.github.admiralxy.agent.domain.ChatMessage;
import io.github.admiralxy.agent.entity.ConversationEntity;
import io.github.admiralxy.agent.repository.ConversationRepository;
import io.github.admiralxy.agent.service.ChatService;
import io.github.admiralxy.agent.service.RagService;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final String CONVERSATION_NOT_FOUND = "Conversation not found";
    private static final String SYSTEM_PROMPT = "Use this additional information for answer:\n%s";
    private static final String SORT_DIRECTION_COLUMN = "createdAt";

    private final ConversationRepository conversationRepository;
    private final RagService ragService;
    private final ChatProperties chatProperties;
    private final RagProperties ragProperties;
    private final ChatClient chatClient;
    private final ChatMemory chatMemory;

    @Override
    public Page<Chat> getAll(int size) {
        Pageable page = PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, SORT_DIRECTION_COLUMN));
        return conversationRepository.findAll(page)
                .map(chat -> new Chat(chat.getId(), chat.getTitle(), chat.getRagSpace()));
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
    public Flux<String> send(UUID id, String text) {
        return Mono.fromCallable(() -> {
                    var conversation = conversationRepository.findById(id)
                            .orElseThrow(() -> new IllegalArgumentException(CONVERSATION_NOT_FOUND));

                    return ragService.buildContext(
                            conversation.getRagSpace(),
                            text,
                            ragProperties.getPercentage(),
                            ragProperties.getMaxChars(),
                            ragProperties.getTopK()
                    );
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(context -> {
                    Flux<String> source = chatClient.prompt()
                            .system(StringUtils.isNotBlank(context) ? SYSTEM_PROMPT.formatted(context) : StringUtils.EMPTY)
                            .user(text)
                            .advisors(a -> a.param(AbstractChatMemoryAdvisor.CHAT_MEMORY_CONVERSATION_ID_KEY, id))
                            .stream()
                            .content();

                    Flux<String> shared = source.publish().autoConnect(2);
                    StringBuilder acc = new StringBuilder();

                    shared
                            .doOnNext(acc::append)
                            .doFinally(ignored -> {
                                try {
                                    String assistantText = acc.toString();
                                    if (!assistantText.isEmpty()) {
                                        chatMemory.add(
                                                id.toString(),
                                                List.of(new AssistantMessage(assistantText))
                                        );
                                    }
                                } catch (Exception e) {
                                    log.error("Failed to persist assistant message to ChatMemory for conversation {}", id, e);
                                }
                            })
                            .onErrorResume(t -> Mono.empty())
                            .subscribe();

                    Flux<String> cumulative = shared
                            .scan(new StringBuilder(), StringBuilder::append)
                            .skip(1)
                            .map(StringBuilder::toString);

                    return cumulative.onErrorResume(t -> {
                        String msg = t.getMessage();
                        boolean disconnected =
                                t instanceof IOException ||
                                        (msg != null && msg.toLowerCase().contains("broken pipe")) ||
                                        (msg != null && msg.toLowerCase().contains("forcibly closed"));
                        return disconnected ? Mono.empty() : Mono.error(t);
                    });
                });
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
