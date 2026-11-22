package io.github.admiralxy.agent.service.model;

import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import io.github.admiralxy.agent.config.AiHttpClientBuilderFactory;
import io.github.admiralxy.agent.config.SessionConfig.SessionChatAdvisors;
import io.github.admiralxy.agent.domain.ThinkingLevel;
import io.github.admiralxy.agent.entity.ChatModelEntity;
import io.github.admiralxy.agent.entity.ChatModelProvider;
import io.github.admiralxy.agent.entity.SummarizerModelEntity;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatModelClientFactory {

    public static final int DEFAULT_ANTHROPIC_MAX_TOKENS = 32000;

    private static final String OPENAI_HOST = "api.openai.com";
    private static final String AGENT_NOT_SUPPORTED = "Agent models run in the agent runner";
    private static final double SUMMARIZER_TEMPERATURE = 0.0;

    private final AiHttpClientBuilderFactory httpClientBuilderFactory;
    private final SessionChatAdvisors sessionChatAdvisors;

    public ChatClient chatClient(ChatModelEntity model, @Nullable ThinkingLevel thinking) {
        ModelSpec spec = new ModelSpec(model.getProvider(), model.getName(), model.getBaseUrl(), model.getApiKey(),
                model.isStreaming(), model.getTemperature(), model.getMaxOutputTokens(),
                model.isThinking() ? thinking : null);
        return ChatClient.builder(createModel(spec)).defaultAdvisors(sessionChatAdvisors.advisors()).build();
    }

    public ChatClient summarizerClient(SummarizerModelEntity model) {
        ModelSpec spec = new ModelSpec(model.getProvider(), model.getName(), model.getBaseUrl(), model.getApiKey(),
                false, SUMMARIZER_TEMPERATURE, 0, null);
        return ChatClient.builder(createModel(spec)).build();
    }

    private ChatModel createModel(ModelSpec spec) {
        return switch (spec.provider()) {
            case OPENAI -> createOpenAiModel(spec);
            case ANTHROPIC -> createAnthropicModel(spec);
            case AGENT -> throw new IllegalArgumentException(AGENT_NOT_SUPPORTED);
        };
    }

    private ChatModel createOpenAiModel(ModelSpec spec) {
        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder()
                .baseUrl(OpenAiBaseUrls.normalize(spec.baseUrl()))
                .apiKey(spec.apiKey())
                .model(spec.name())
                .streamUsage(spec.streaming());
        if (spec.maxOutputTokens() > 0) {
            if (Strings.CI.contains(spec.baseUrl(), OPENAI_HOST)) {
                options.maxCompletionTokens(spec.maxOutputTokens());
            } else {
                options.maxTokens(spec.maxOutputTokens());
            }
        }
        if (spec.thinking() != null && spec.thinking() != ThinkingLevel.OFF) {
            options.reasoningEffort(spec.thinking().effort());
        } else {
            options.temperature(spec.temperature());
        }
        return OpenAiChatModel.builder()
                .options(options.build())
                .httpClientBuilderCustomizer(httpClientBuilderFactory.openAiHttpClientCustomizer())
                .build();
    }

    private ChatModel createAnthropicModel(ModelSpec spec) {
        AnthropicChatOptions.Builder options = AnthropicChatOptions.builder()
                .apiKey(spec.apiKey())
                .model(spec.name())
                .maxTokens(spec.maxOutputTokens() > 0 ? spec.maxOutputTokens() : DEFAULT_ANTHROPIC_MAX_TOKENS);
        if (StringUtils.isNotBlank(spec.baseUrl())) {
            options.baseUrl(spec.baseUrl());
        }
        if (spec.thinking() == ThinkingLevel.OFF) {
            options.thinkingDisabled();
        } else if (spec.thinking() != null) {
            options.thinkingAdaptive(ThinkingConfigAdaptive.Display.SUMMARIZED)
                    .effort(OutputConfig.Effort.of(spec.thinking().effort()));
        }
        return AnthropicChatModel.builder()
                .options(options.build())
                .httpClientBuilderCustomizer(httpClientBuilderFactory.anthropicHttpClientCustomizer())
                .build();
    }

    private record ModelSpec(
            ChatModelProvider provider,
            String name,
            String baseUrl,
            String apiKey,
            boolean streaming,
            double temperature,
            int maxOutputTokens,
            @Nullable ThinkingLevel thinking
    ) {
    }
}
