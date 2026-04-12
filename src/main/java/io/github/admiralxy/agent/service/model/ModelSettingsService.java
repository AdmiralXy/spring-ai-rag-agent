package io.github.admiralxy.agent.service.model;

import io.github.admiralxy.agent.entity.ChatModelSettingsEntity;
import io.github.admiralxy.agent.entity.EmbeddingsModelSettingsEntity;
import io.github.admiralxy.agent.entity.SummarizerModelSettingsEntity;

import java.util.List;
import java.util.UUID;

public interface ModelSettingsService {

    List<ChatModelSettingsEntity> getChatModels();

    ChatModelSettingsEntity getChatModel(UUID id);

    ChatModelSettingsEntity createChatModel(ChatModelSettingsEntity entity);

    ChatModelSettingsEntity updateChatModel(UUID id, ChatModelSettingsEntity entity);

    void deleteChatModel(UUID id);

    EmbeddingsModelSettingsEntity getEmbeddingsModel();

    EmbeddingsModelSettingsEntity updateEmbeddingsModel(EmbeddingsModelSettingsEntity entity);

    SummarizerModelSettingsEntity getSummarizerModel();

    SummarizerModelSettingsEntity updateSummarizerModel(SummarizerModelSettingsEntity entity);
}
