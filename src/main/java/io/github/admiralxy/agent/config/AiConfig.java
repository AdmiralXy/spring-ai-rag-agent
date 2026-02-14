package io.github.admiralxy.agent.config;

import io.github.admiralxy.agent.config.properties.AppProperties;
import io.github.admiralxy.agent.config.properties.ModelProperties;
import io.github.admiralxy.agent.config.properties.ModelsProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class AiConfig {

    private static final String MODELS_PROMPTS_FOLDER_NAME = "prompts";
    private static final String PROMPT_FILE_PATH_TEMPLATE = "%s/%s.md";

    private static final String LOG_NO_PROMPT = "No system prompt found for model {}";
    private static final String LOG_LOADED_PROMPT = "Loaded system prompt for model {}";
    private static final String LOG_FAILED_PROMPT = "Failed to load system prompt for model {}: {}";

    private static final String CLAUDE_PREFIX = "claude";

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel, ChatMemory chatMemory) {
        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    public Map<String, ChatClient> chatClients(AppProperties props, ChatMemory chatMemory) {
        var promptsPath = props.getConfigPath() + "/" + MODELS_PROMPTS_FOLDER_NAME;

        return props.getModels().stream()
                .collect(Collectors.toMap(
                        ModelsProperties::getAlias,
                        model -> buildChatClient(model, chatMemory, promptsPath)
                ));
    }

    private ChatClient buildChatClient(ModelsProperties model,
                                       ChatMemory chatMemory,
                                       String promptsPath) {

        ModelProperties properties = model.getProperties();

        if (StringUtils.isBlank(properties.getSystemPrompt())) {
            properties.setSystemPrompt(loadSystemPrompt(promptsPath, model.getName()));
        }

        ChatModel chatModel = createChatModel(model);

        return ChatClient.builder(chatModel)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    private ChatModel createChatModel(ModelsProperties model) {
        if (isClaudeModel(model.getName())) {
            return createAnthropicModel(model);
        }
        return createOpenAiModel(model);
    }

    private boolean isClaudeModel(String modelName) {
        return StringUtils.startsWithIgnoreCase(modelName, CLAUDE_PREFIX);
    }

    private ChatModel createAnthropicModel(ModelsProperties model) {
        ModelProperties properties = model.getProperties();

        AnthropicApi api = StringUtils.isNotBlank(model.getBaseUrl())
                ? new AnthropicApi(model.getBaseUrl(), model.getApiKey())
                : new AnthropicApi(model.getApiKey());

        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .withModel(model.getName())
                .withTemperature(properties.getTemperature())
                .withMaxTokens(32000)
                .build();

        return new AnthropicChatModel(api, options);
    }

    private ChatModel createOpenAiModel(ModelsProperties model) {
        ModelProperties properties = model.getProperties();

        OpenAiApi api = new OpenAiApi(model.getBaseUrl(), model.getApiKey());

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .withModel(model.getName())
                .withTemperature(properties.getTemperature())
                .withStreamUsage(properties.isStreaming())
                .build();

        return new OpenAiChatModel(api, options);
    }

    private String loadSystemPrompt(String path, String modelName) {
        try {
            String promptPath = PROMPT_FILE_PATH_TEMPLATE.formatted(path, modelName);
            Resource resource = new DefaultResourceLoader().getResource(promptPath);

            if (!resource.exists()) {
                log.warn(LOG_NO_PROMPT, modelName);
                return null;
            }

            try (InputStream in = resource.getInputStream();
                 InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                String prompt = FileCopyUtils.copyToString(reader);
                log.info(LOG_LOADED_PROMPT, modelName);
                return prompt;
            }
        } catch (Exception e) {
            log.error(LOG_FAILED_PROMPT, modelName, e.getMessage());
            return null;
        }
    }
}
