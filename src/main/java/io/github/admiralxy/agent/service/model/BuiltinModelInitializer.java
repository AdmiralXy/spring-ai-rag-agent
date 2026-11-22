package io.github.admiralxy.agent.service.model;

import io.github.admiralxy.agent.config.properties.BuiltinModelProperties;
import io.github.admiralxy.agent.entity.ChatModelProvider;
import io.github.admiralxy.agent.repository.EmbeddingModelRepository;
import io.github.admiralxy.agent.repository.RagDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Keeps the built-in chat model in sync with the configuration (it is never edited through the API) and creates
 * the vector indexes for the embedding dimensions in use.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinModelInitializer {

    private static final String UPSERT = """
            INSERT INTO t_chat_models (c_id, c_user_id, c_builtin, c_provider, c_label, c_name, c_base_url, c_api_key,
                c_streaming, c_system_prompt, c_priority, c_temperature, c_max_context_tokens, c_max_output_tokens,
                c_thinking, c_vision)
            VALUES (?, NULL, true, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, false, false)
            ON CONFLICT (c_id) DO UPDATE SET c_provider = excluded.c_provider, c_label = excluded.c_label,
                c_name = excluded.c_name, c_base_url = excluded.c_base_url, c_api_key = excluded.c_api_key,
                c_streaming = excluded.c_streaming, c_system_prompt = excluded.c_system_prompt,
                c_priority = excluded.c_priority, c_temperature = excluded.c_temperature,
                c_max_context_tokens = excluded.c_max_context_tokens,
                c_max_output_tokens = excluded.c_max_output_tokens, c_updated_at = now()""";
    private static final String DELETE = "DELETE FROM t_chat_models WHERE c_id = ?";
    private static final String READY = "Built-in model {} ({}) is available to all users";
    private static final String DISABLED = "Built-in model is disabled";
    private static final String NO_KEY = "Built-in model has no API key (app.builtin-model.api-key), it is disabled";
    private static final String PROMPT_FAILED = "Cannot read the system prompt of the built-in model";

    private final BuiltinModelProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModelRepository embeddingModelRepository;
    private final RagDocumentRepository ragDocumentRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        syncBuiltinModel();
        embeddingModelRepository.findDistinctDimensions().forEach(ragDocumentRepository::ensureVectorIndex);
    }

    void syncBuiltinModel() {
        if (!properties.isEnabled() || StringUtils.isBlank(properties.getApiKey())) {
            log.info(properties.isEnabled() ? NO_KEY : DISABLED);
            jdbcTemplate.update(DELETE, BuiltinModelProperties.ID);
            return;
        }
        jdbcTemplate.update(UPSERT, BuiltinModelProperties.ID, ChatModelProvider.OPENAI.name(), properties.getLabel(),
                properties.getName(), StringUtils.defaultString(properties.getBaseUrl()), properties.getApiKey(),
                properties.isStreaming(), systemPrompt(properties.getSystemPrompt()), properties.getPriority(),
                properties.getTemperature(), properties.getMaxContextTokens(),
                Math.max(0, properties.getMaxOutputTokens()));
        log.info(READY, properties.getLabel(), properties.getName());
    }

    private static String systemPrompt(Resource resource) {
        if (resource == null || !resource.exists()) {
            return null;
        }
        try {
            return StringUtils.trimToNull(resource.getContentAsString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(PROMPT_FAILED, e);
        }
    }
}
