package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.domain.Chat;
import io.github.admiralxy.agent.domain.ChatAttachment;
import io.github.admiralxy.agent.domain.ChatMessage;
import io.github.admiralxy.agent.domain.ChatRole;
import io.github.admiralxy.agent.domain.ThinkingLevel;
import io.github.admiralxy.agent.entity.ChatEntity;
import io.github.admiralxy.agent.entity.ChatModelEntity;
import io.github.admiralxy.agent.exception.ChatBusyException;
import io.github.admiralxy.agent.repository.ChatRepository;
import io.github.admiralxy.agent.service.agent.AgentRunnerClient;
import io.github.admiralxy.agent.service.model.ModelSettingsService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final String CHAT_NOT_FOUND = "Chat not found: %s";
    private static final String EMPTY_MESSAGE = "The message is empty";
    private static final String ANSWER_IN_PROGRESS = "An answer is being generated, wait for it or stop it first";
    private static final String NO_STREAMER = "No answer streamer for provider %s";
    private static final String IMAGE_ONLY_TITLE = "Image";
    private static final String SORT_COLUMN = "updatedAt";
    private static final String REJECTED = "Message to chat {} rejected: {}";
    private static final String START_FAILED = "Failed to start the answer for chat {}";

    private final ChatRepository chatRepository;
    private final ModelSettingsService modelSettingsService;
    private final ChatSessionMemory sessionMemory;
    private final ChatTranscript transcript;
    private final ChatTitleGenerator titleGenerator;
    private final ChatStreams streams;
    private final ChatEvents events;
    private final ChatErrors errors;
    private final List<AnswerStreamer> streamers;
    private final AgentRunnerClient agentRunnerClient;

    @Override
    public Page<Chat> getAll(int page, int size, @Nullable String query) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, SORT_COLUMN));
        Page<ChatEntity> chats = StringUtils.isBlank(query)
                ? chatRepository.findAll(pageable)
                : chatRepository.findByTitleContainingIgnoreCase(query.trim(), pageable);
        return chats.map(ChatServiceImpl::toDomain);
    }

    @Override
    public Chat create(Collection<UUID> spaceIds) {
        ChatEntity chat = new ChatEntity();
        chat.setTitle(String.valueOf(Instant.now().toEpochMilli()));
        chat.getSpaceIds().addAll(spaceIds);
        return toDomain(chatRepository.save(chat));
    }

    @Override
    public UUID updateModel(UUID chatId, UUID modelId) {
        ChatModelEntity model = modelSettingsService.getChatModel(modelId);
        ChatEntity chat = getChat(chatId);
        chat.setModelId(model.getId());
        chatRepository.save(chat);
        return model.getId();
    }

    @Override
    public Flux<ServerSentEvent<String>> send(UUID chatId, SendMessageCommand command) {
        return Mono.fromCallable(() -> prepare(chatId, command))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(this::start)
                .onErrorResume(e -> {
                    log.warn(REJECTED, chatId, e.getMessage());
                    return Flux.just(events.error(errors.describe(e)), events.rejected());
                });
    }

    @Override
    public boolean stop(UUID chatId) {
        return streams.stop(chatId);
    }

    @Override
    public List<ChatMessage> history(UUID chatId) {
        return transcript.history(chatId);
    }

    @Override
    public int deleteMessages(UUID chatId, Collection<UUID> messageIds) {
        getChat(chatId);
        if (streams.isActive(chatId)) {
            throw new ChatBusyException(ANSWER_IN_PROGRESS);
        }
        if (messageIds == null || messageIds.isEmpty()) {
            return 0;
        }
        int deleted = transcript.delete(chatId, messageIds);
        if (deleted > 0) {
            sessionMemory.rebuild(chatId);
            agentRunnerClient.resetSession(chatId);
        }
        return deleted;
    }

    @Override
    public void delete(UUID chatId) {
        ChatEntity chat = getChat(chatId);
        streams.stop(chatId);
        chatRepository.delete(chat);
        sessionMemory.delete(chatId);
        agentRunnerClient.deleteChat(chatId);
    }

    private PreparedMessage prepare(UUID chatId, SendMessageCommand command) {
        ChatModelEntity model = modelSettingsService.getChatModel(command.modelId());
        List<ChatAttachment> attachments = ChatImages.validate(model, command.images());
        String text = StringUtils.defaultString(command.text());
        if (text.isBlank() && attachments.isEmpty()) {
            throw new IllegalArgumentException(EMPTY_MESSAGE);
        }
        ThinkingLevel thinking = model.isThinking() ? ThinkingLevel.parse(command.thinking()) : null;
        return new PreparedMessage(getChat(chatId), model, text, attachments, thinking);
    }

    private Flux<ServerSentEvent<String>> start(PreparedMessage message) {
        ChatEntity chat = message.chat();
        UUID chatId = chat.getId();
        chatRepository.touch(chatId, Instant.now());
        sessionMemory.ensureSession(chatId);
        String previousQuestion = sessionMemory.lastUserText(chatId);
        titleGenerator.generateIfFirstMessage(chat, message.text().isBlank() ? IMAGE_ONLY_TITLE : message.text());
        UUID questionId = transcript.saveQuestion(chatId, message.text(), message.attachments());

        AnswerRequest request = new AnswerRequest(chatId, message.model(), message.text(), message.attachments(),
                new ArrayList<>(chat.getSpaceIds()), previousQuestion, message.thinking());
        AnswerStreamer streamer = streamer(message.model());
        Flux<ServerSentEvent<String>> answer = Flux.defer(() -> streamer.stream(request))
                .onErrorResume(e -> {
                    log.warn(START_FAILED, chatId, e);
                    String error = errors.describe(e);
                    UUID answerId = transcript.saveFailure(chatId, StringUtils.EMPTY, StringUtils.EMPTY, error);
                    return Flux.fromIterable(events.failure(answerId, error));
                });
        Mono<ServerSentEvent<String>> question = Mono.just(events.message(ChatRole.USER, questionId));
        return streams.runDetached(chatId, Flux.concat(question, answer));
    }

    private AnswerStreamer streamer(ChatModelEntity model) {
        return streamers.stream()
                .filter(s -> s.supports(model.getProvider()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(NO_STREAMER.formatted(model.getProvider())));
    }

    private ChatEntity getChat(UUID chatId) {
        return chatRepository.findById(chatId)
                .orElseThrow(() -> new IllegalArgumentException(CHAT_NOT_FOUND.formatted(chatId)));
    }

    private static Chat toDomain(ChatEntity chat) {
        return new Chat(chat.getId(), chat.getTitle(), chat.getModelId(), List.copyOf(chat.getSpaceIds()),
                chat.getCreatedAt(), chat.getUpdatedAt());
    }

    private record PreparedMessage(
            ChatEntity chat,
            ChatModelEntity model,
            String text,
            List<ChatAttachment> attachments,
            @Nullable ThinkingLevel thinking
    ) {
    }
}
