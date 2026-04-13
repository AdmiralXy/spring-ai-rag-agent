package io.github.admiralxy.agent.service.model;

import io.github.admiralxy.agent.config.AiHttpClientBuilderFactory;
import io.github.admiralxy.agent.entity.ChatModelProvider;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

@Component
public class ChatModelClientFactory {

    private static final int DEFAULT_ANTHROPIC_MAX_TOKENS = 32000;
    private final AiHttpClientBuilderFactory httpClientBuilderFactory;

    public ChatModelClientFactory(AiHttpClientBuilderFactory httpClientBuilderFactory) {
        this.httpClientBuilderFactory = httpClientBuilderFactory;
    }

    public ChatClient createWithMemory(ChatModelProvider provider,
                                       String model,
                                       String baseUrl,
                                       String apiKey,
                                       boolean streaming,
                                       double temperature,
                                       ChatMemory chatMemory) {
        ChatClient.Builder builder = ChatClient.builder(createModel(provider, model, baseUrl, apiKey, streaming, temperature));
        return builder.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build()).build();
    }

    public ChatClient create(ChatModelProvider provider,
                             String model,
                             String baseUrl,
                             String apiKey,
                             boolean streaming,
                             double temperature) {
        return ChatClient.builder(createModel(provider, model, baseUrl, apiKey, streaming, temperature)).build();
    }

    private ChatModel createModel(ChatModelProvider provider,
                                  String model,
                                  String baseUrl,
                                  String apiKey,
                                  boolean streaming,
                                  double temperature) {
        return switch (provider) {
            case OPENAI -> createOpenAiModel(model, baseUrl, apiKey, streaming, temperature);
            case ANTHROPIC -> createAnthropicModel(model, baseUrl, apiKey, temperature);
        };
    }

    private ChatModel createOpenAiModel(String model,
                                        String baseUrl,
                                        String apiKey,
                                        boolean streaming,
                                        double temperature) {
        OpenAiApi api = OpenAiApi.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .restClientBuilder(httpClientBuilderFactory.createRestClientBuilder())
                .webClientBuilder(httpClientBuilderFactory.createWebClientBuilder())
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .streamUsage(streaming)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(options)
                .build();
    }

    private ChatModel createAnthropicModel(String model,
                                           String baseUrl,
                                           String apiKey,
                                           double temperature) {
        AnthropicApi.Builder apiBuilder = AnthropicApi.builder()
                .apiKey(apiKey)
                .restClientBuilder(httpClientBuilderFactory.createRestClientBuilder())
                .webClientBuilder(httpClientBuilderFactory.createWebClientBuilder());
        if (baseUrl != null && !baseUrl.isBlank()) {
            apiBuilder.baseUrl(baseUrl);
        }

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(DEFAULT_ANTHROPIC_MAX_TOKENS)
                .build();

        return AnthropicChatModel.builder()
                .anthropicApi(apiBuilder.build())
                .defaultOptions(options)
                .build();
    }
}
