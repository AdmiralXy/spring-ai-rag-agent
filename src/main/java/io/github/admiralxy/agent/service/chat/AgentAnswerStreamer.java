package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.config.properties.ChatProperties;
import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.ChatPartType;
import io.github.admiralxy.agent.domain.ChatRole;
import io.github.admiralxy.agent.entity.ChatModelEntity;
import io.github.admiralxy.agent.entity.ChatModelProvider;
import io.github.admiralxy.agent.service.agent.AgentMessageAccumulator;
import io.github.admiralxy.agent.service.agent.AgentQuery;
import io.github.admiralxy.agent.service.agent.AgentRunnerClient;
import io.github.admiralxy.agent.service.rag.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentAnswerStreamer implements AnswerStreamer {

    private static final String CONTEXT_PROMPT = """
            Additional information from the user's knowledge base (RAG search results), use it if relevant:
            <knowledge_base>
            %s
            </knowledge_base>

            %s""";
    private static final String NO_TEXT_ANSWER = "(the agent finished without a text answer)";
    private static final String ROLE = "role";
    private static final String CONTENT = "content";
    private static final String MALFORMED_EVENT = "Malformed agent event {} for chat {}";
    private static final String STREAM_FAILED = "Agent answer failed for chat {}: {}";
    private static final String SAVE_FAILED = "Failed to store the agent answer of chat {}";

    private final AgentRunnerClient agentRunnerClient;
    private final RagService ragService;
    private final RagProperties ragProperties;
    private final ChatProperties chatProperties;
    private final ChatSessionMemory sessionMemory;
    private final ChatTranscript transcript;
    private final ChatEvents events;
    private final ChatErrors errors;
    private final JsonMapper jsonMapper;

    @Override
    public boolean supports(ChatModelProvider provider) {
        return provider == ChatModelProvider.AGENT;
    }

    @Override
    public Flux<ServerSentEvent<String>> stream(AnswerRequest request) {
        ChatModelEntity model = request.model();
        String context = ragService.buildContext(request.userId(), request.spaceIds(), request.text(), request.previousQuestion(),
                ContextBudget.of(model).agentRagTokens(ragProperties));
        List<Map<String, String>> history = sessionMemory.lastMessages(request.chatId(), chatProperties.getHistoryLimit())
                .stream()
                .map(m -> Map.of(ROLE, m.getMessageType().name(), CONTENT, StringUtils.defaultString(m.getText())))
                .toList();
        sessionMemory.append(request.chatId(), ChatSessionMemory.userMessage(request.prompt(), request.attachments()));

        String prompt = StringUtils.isBlank(context)
                ? request.prompt()
                : CONTEXT_PROMPT.formatted(context, request.prompt());
        AgentQuery query = new AgentQuery(
                prompt,
                model.getName(),
                model.getApiKey(),
                model.getSystemPrompt(),
                agentRunnerClient.maxTurns(),
                history,
                request.thinking() == null ? null : request.thinking().effort(),
                request.attachments().isEmpty() ? null : request.attachments()
        );

        Run run = new Run(request.chatId());
        return agentRunnerClient.query(request.chatId(), query)
                .doOnNext(run::accept)
                .concatMap(run::forward)
                .onErrorResume(run::fail)
                .doFinally(ignored -> run.persist());
    }

    private final class Run {

        private final UUID chatId;
        private final AgentMessageAccumulator accumulator = new AgentMessageAccumulator();
        private boolean persisted;

        private Run(UUID chatId) {
            this.chatId = chatId;
        }

        private void accept(ServerSentEvent<String> event) {
            try {
                accumulator.accept(event.event(), jsonMapper.readTree(event.data()));
            } catch (RuntimeException e) {
                log.warn(MALFORMED_EVENT, event.event(), chatId, e);
            }
        }

        private Flux<ServerSentEvent<String>> forward(ServerSentEvent<String> event) {
            ServerSentEvent<String> forwarded = events.raw(event.event(), event.data());
            if (!ChatEvents.DONE.equals(event.event())) {
                return Flux.just(forwarded);
            }
            return persistAsync().flatMapMany(id -> id
                    .map(value -> Flux.just(events.message(ChatRole.ASSISTANT, value), forwarded))
                    .orElseGet(() -> Flux.just(forwarded)));
        }

        private Flux<ServerSentEvent<String>> fail(Throwable e) {
            String message = errors.describeAgent(e);
            log.warn(STREAM_FAILED, chatId, message);
            accumulator.addError(message);
            return persistAsync().flatMapIterable(id -> events.failure(id.orElse(null), message));
        }

        private Mono<Optional<UUID>> persistAsync() {
            return Mono.fromCallable(() -> Optional.ofNullable(persist())).subscribeOn(Schedulers.boundedElastic());
        }

        private synchronized UUID persist() {
            if (persisted) {
                return null;
            }
            persisted = true;
            try {
                UUID id = null;
                if (!accumulator.isEmpty()) {
                    boolean onlyErrors = accumulator.parts().stream().allMatch(p -> p.type() == ChatPartType.ERROR);
                    String content = onlyErrors ? StringUtils.EMPTY : StringUtils.defaultIfBlank(accumulator.text(), NO_TEXT_ANSWER);
                    id = transcript.saveAnswer(chatId, content, accumulator.parts(), accumulator.stats());
                }
                if (StringUtils.isNotBlank(accumulator.text())) {
                    sessionMemory.append(chatId, new AssistantMessage(accumulator.text()));
                }
                return id;
            } catch (RuntimeException e) {
                log.error(SAVE_FAILED, chatId, e);
                return null;
            }
        }
    }
}
