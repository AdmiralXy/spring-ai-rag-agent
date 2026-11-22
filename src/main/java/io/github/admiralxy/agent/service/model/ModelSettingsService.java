package io.github.admiralxy.agent.service.model;

import io.github.admiralxy.agent.entity.ChatModelEntity;
import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.entity.SummarizerModelEntity;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface ModelSettingsService {

    List<ChatModelEntity> getChatModels();

    ChatModelEntity getChatModel(UUID id);

    ChatModelEntity createChatModel(Consumer<ChatModelEntity> settings);

    ChatModelEntity updateChatModel(UUID id, Consumer<ChatModelEntity> settings);

    void deleteChatModel(UUID id);

    EmbeddingModelEntity getEmbeddingModel();

    EmbeddingModelEntity updateEmbeddingModel(Consumer<EmbeddingModelEntity> settings);

    SummarizerModelEntity getSummarizerModel();

    SummarizerModelEntity updateSummarizerModel(Consumer<SummarizerModelEntity> settings);
}
