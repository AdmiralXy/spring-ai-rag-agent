package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.ChatRole;
import io.github.admiralxy.agent.entity.ChatModelEntity;
import io.github.admiralxy.agent.entity.ChatModelProvider;
import io.github.admiralxy.agent.service.model.ChatModelClientFactory;
import io.github.admiralxy.agent.service.rag.RagService;
import io.github.admiralxy.agent.service.rag.TokenizerService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.part.ReasoningPart;
import org.springframework.ai.chat.metadata.FinishReasonCategory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.content.Media;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.advisor.SessionMemoryAdvisor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ModelAnswerStreamer implements AnswerStreamer {

    private static final String CONTEXT_PROMPT = """
            Below are excerpts from the user's knowledge base found for this conversation. Prefer them over \
            general knowledge when they are relevant and mention the source (path or title) you rely on. If they \
            do not cover the question, say so and answer from general knowledge.
            <knowledge_base>
            %s
            </knowledge_base>""";
    private static final String PARAGRAPH = "\n\n";
    private static final String PROMPT_TOO_LARGE = "The message with its knowledge-base context needs about %d "
            + "tokens, more than the model's context window (%d tokens, %d of them reserved for the answer) leaves. "
            + "Shorten the message or check \"Max context tokens\" in the model settings.";
    private static final String TRUNCATED_NOTICE = "The answer reached the output limit (%s) and was cut off. "
            + "Ask to continue, or raise \"Max output tokens\" in the model settings.";
    private static final String LIMIT_TOKENS = "%d tokens";
    private static final String LIMIT_DEFAULT = "the provider's default";
    private static final String NOTICE_TRUNCATED = "truncated";
    private static final String STREAM_FAILED = "Model answer failed for chat {}";
    private static final String SAVE_FAILED = "Failed to store the answer of chat {}";

    private final ChatModelClientFactory clientFactory;
    private final RagService ragService;
    private final RagProperties ragProperties;
    private final ChatSessionMemory sessionMemory;
    private final TokenizerService tokenizerService;
    private final ChatTranscript transcript;
    private final ChatEvents events;
    private final ChatErrors errors;

    @Override
    public boolean supports(ChatModelProvider provider) {
        return provider != ChatModelProvider.AGENT;
    }

    @Override
    public Flux<ServerSentEvent<String>> stream(AnswerRequest request) {
        ChatModelEntity model = request.model();
        ContextBudget budget = ContextBudget.of(model);
        String context = ragService.buildContext(request.spaceIds(), request.text(), request.previousQuestion(),
                budget.ragTokens(ragProperties));
        String systemPrompt = systemPrompt(model.getSystemPrompt(), context);
        EventFilter history = historyWindow(request, budget, systemPrompt);

        Media[] media = ChatSessionMemory.toMedia(request.attachments()).toArray(Media[]::new);
        ChatClient.ChatClientRequestSpec spec = clientFactory.chatClient(model, request.thinking()).prompt()
                .user(u -> u.text(request.prompt()).media(media))
                .advisors(a -> {
                    a.param(SessionMemoryAdvisor.SESSION_ID_CONTEXT_KEY, request.chatId().toString());
                    if (history != null) {
                        a.param(SessionMemoryAdvisor.EVENT_FILTER_CONTEXT_KEY, history);
                    }
                });
        if (StringUtils.isNotBlank(systemPrompt)) {
            spec = spec.system(systemPrompt);
        }
        ChatClient.ChatClientRequestSpec finalSpec = spec;
        Flux<ChatResponse> responses = model.isStreaming()
                ? finalSpec.stream().chatResponse()
                : Mono.fromCallable(() -> Objects.requireNonNull(finalSpec.call().chatResponse())).flux();

        Answer answer = new Answer(request);
        return responses
                .concatMapIterable(answer::accept)
                .onErrorResume(answer::fail)
                .concatWith(Flux.defer(answer::finish).subscribeOn(Schedulers.boundedElastic()))
                .doFinally(answer::cancelled);
    }

    @Nullable
    private EventFilter historyWindow(AnswerRequest request, ContextBudget budget, String systemPrompt) {
        int requestTokens = tokenizerService.countTokens(systemPrompt) + tokenizerService.countTokens(request.prompt())
                + request.attachments().size() * ChatSessionMemory.IMAGE_TOKENS;
        int historyBudget = budget.inputTokens() - requestTokens;
        if (historyBudget < 0) {
            throw new IllegalStateException(PROMPT_TOO_LARGE.formatted(requestTokens, budget.window(),
                    budget.outputTokens()));
        }
        return sessionMemory.historyWindow(request.chatId(), historyBudget);
    }

    private static String systemPrompt(@Nullable String systemPrompt, @Nullable String context) {
        List<String> sections = new ArrayList<>(2);
        if (StringUtils.isNotBlank(systemPrompt)) {
            sections.add(systemPrompt);
        }
        if (StringUtils.isNotBlank(context)) {
            sections.add(CONTEXT_PROMPT.formatted(context));
        }
        return String.join(PARAGRAPH, sections);
    }

    private static String truncatedNotice(ChatModelEntity model) {
        String limit = model.getMaxOutputTokens() > 0
                ? LIMIT_TOKENS.formatted(model.getMaxOutputTokens())
                : LIMIT_DEFAULT;
        return TRUNCATED_NOTICE.formatted(limit);
    }

    private final class Answer {

        private final AnswerRequest request;
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        private volatile String failure;
        private volatile boolean truncated;
        private volatile boolean persisted;
        private volatile UUID id;

        private Answer(AnswerRequest request) {
            this.request = request;
        }

        private List<ServerSentEvent<String>> accept(ChatResponse response) {
            Generation generation = response.getResult();
            if (generation == null) {
                return List.of();
            }
            if (generation.getMetadata() != null
                    && generation.getMetadata().getFinishReasonCategory() == FinishReasonCategory.TRUNCATED) {
                truncated = true;
            }
            AssistantMessage output = generation.getOutput();
            List<ServerSentEvent<String>> result = new ArrayList<>(2);
            String thought = output.getReasoning().stream()
                    .map(ReasoningPart::text)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining());
            if (!thought.isEmpty()) {
                reasoning.append(thought);
                result.add(events.thinking(thought));
            }
            String delta = output.getText();
            if (StringUtils.isNotEmpty(delta)) {
                text.append(delta);
                result.add(events.text(delta));
            }
            return result;
        }

        private Flux<ServerSentEvent<String>> fail(Throwable e) {
            log.warn(STREAM_FAILED, request.chatId(), e);
            failure = errors.describe(e);
            return Flux.just(events.error(failure));
        }

        private Flux<ServerSentEvent<String>> finish() {
            persist();
            List<ServerSentEvent<String>> tail = new ArrayList<>(3);
            if (truncated && failure == null) {
                tail.add(events.notice(NOTICE_TRUNCATED, truncatedNotice(request.model())));
            }
            if (id != null) {
                tail.add(events.message(ChatRole.ASSISTANT, id));
            }
            tail.add(failure != null ? events.failed() : events.done());
            return Flux.fromIterable(tail);
        }

        private void cancelled(SignalType signal) {
            if (signal != SignalType.CANCEL) {
                return;
            }
            persist();
            if (!text.isEmpty()) {
                sessionMemory.append(request.chatId(), new AssistantMessage(text.toString()));
            }
        }

        private synchronized void persist() {
            if (persisted) {
                return;
            }
            persisted = true;
            String answerText = text.toString();
            String reasoningText = reasoning.toString();
            if (failure != null) {
                id = transcript.saveFailure(request.chatId(), reasoningText, answerText, failure);
                return;
            }
            if (answerText.isEmpty() && reasoningText.isEmpty()) {
                return;
            }
            try {
                String notice = truncated ? truncatedNotice(request.model()) : null;
                id = transcript.saveAnswer(request.chatId(), answerText,
                        ChatTranscript.answerParts(reasoningText, answerText, null, notice), null);
            } catch (RuntimeException e) {
                log.error(SAVE_FAILED, request.chatId(), e);
            }
        }
    }
}
