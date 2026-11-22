package io.github.admiralxy.agent.controller.mapper;

import io.github.admiralxy.agent.controller.request.settings.ChatModelUpsertRq;
import io.github.admiralxy.agent.controller.request.settings.EmbeddingsModelUpdateRq;
import io.github.admiralxy.agent.controller.request.settings.SummarizerModelUpdateRq;
import io.github.admiralxy.agent.controller.response.chat.ModelInfoRs;
import io.github.admiralxy.agent.controller.response.settings.ChatModelRs;
import io.github.admiralxy.agent.controller.response.settings.EmbeddingsModelRs;
import io.github.admiralxy.agent.controller.response.settings.SummarizerModelRs;
import io.github.admiralxy.agent.entity.ChatModelEntity;
import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.entity.SummarizerModelEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class ModelSettingsMapper {

    public void apply(ChatModelEntity entity, ChatModelUpsertRq rq) {
        entity.setProvider(rq.provider());
        entity.setLabel(StringUtils.trimToEmpty(rq.label()));
        entity.setName(StringUtils.trimToEmpty(rq.name()));
        entity.setBaseUrl(StringUtils.trimToEmpty(rq.baseUrl()));
        entity.setApiKey(StringUtils.trimToEmpty(rq.apiKey()));
        entity.setStreaming(rq.streaming());
        entity.setSystemPrompt(StringUtils.trimToNull(rq.systemPrompt()));
        entity.setPriority(rq.priority());
        entity.setTemperature(rq.temperature());
        entity.setMaxContextTokens(rq.maxContextTokens());
        entity.setMaxOutputTokens(Math.max(0, rq.maxOutputTokens()));
        entity.setThinking(rq.thinking());
        entity.setVision(rq.vision());
    }

    public void apply(EmbeddingModelEntity entity, EmbeddingsModelUpdateRq rq) {
        entity.setProvider(rq.provider());
        entity.setBaseUrl(StringUtils.trimToEmpty(rq.baseUrl()));
        entity.setApiKey(StringUtils.trimToEmpty(rq.apiKey()));
        entity.setName(StringUtils.trimToEmpty(rq.name()));
        entity.setDimensions(rq.dimensions());
        entity.setMaxDocumentTokens(rq.maxDocumentTokens());
    }

    public void apply(SummarizerModelEntity entity, SummarizerModelUpdateRq rq) {
        entity.setProvider(rq.provider());
        entity.setName(StringUtils.trimToEmpty(rq.name()));
        entity.setBaseUrl(StringUtils.trimToEmpty(rq.baseUrl()));
        entity.setApiKey(StringUtils.trimToEmpty(rq.apiKey()));
        entity.setSystemPrompt(StringUtils.trimToNull(rq.systemPrompt()));
    }

    public ChatModelRs toRs(ChatModelEntity entity) {
        return new ChatModelRs(
                entity.getId(),
                entity.getProvider(),
                entity.getLabel(),
                entity.getName(),
                entity.getBaseUrl(),
                entity.getApiKey(),
                entity.isStreaming(),
                entity.getSystemPrompt(),
                entity.getPriority(),
                entity.getTemperature(),
                entity.getMaxContextTokens(),
                entity.isThinking(),
                entity.getMaxOutputTokens(),
                entity.isVision()
        );
    }

    public EmbeddingsModelRs toRs(EmbeddingModelEntity entity) {
        return new EmbeddingsModelRs(
                entity.getProvider(),
                entity.getBaseUrl(),
                entity.getApiKey(),
                entity.getName(),
                entity.getDimensions(),
                entity.getMaxDocumentTokens()
        );
    }

    public SummarizerModelRs toRs(SummarizerModelEntity entity) {
        return new SummarizerModelRs(
                entity.getProvider(),
                entity.getName(),
                entity.getBaseUrl(),
                entity.getApiKey(),
                entity.getSystemPrompt()
        );
    }

    public ModelInfoRs toInfo(ChatModelEntity entity) {
        return new ModelInfoRs(entity.getLabel(), entity.getId().toString(), entity.getProvider(),
                entity.isThinking(), entity.isVision());
    }
}
