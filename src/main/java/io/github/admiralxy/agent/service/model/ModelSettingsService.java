package io.github.admiralxy.agent.service.model;

import io.github.admiralxy.agent.entity.ChatModelEntity;
import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.entity.SummarizerModelEntity;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public interface ModelSettingsService {

    /**
     * Models of the user plus the built-in one, by priority.
     */
    List<ChatModelEntity> getChatModels(UUID userId);

    ChatModelEntity getChatModel(UUID userId, UUID id);

    ChatModelEntity createChatModel(UUID userId, Consumer<ChatModelEntity> settings);

    ChatModelEntity updateChatModel(UUID userId, UUID id, Consumer<ChatModelEntity> settings);

    void deleteChatModel(UUID userId, UUID id);

    EmbeddingModelEntity getEmbeddingModel(UUID userId);

    /**
     * Saves the embedding model. When the change makes the stored vectors incomparable with new ones (another
     * model, URL or dimension), the chunks of all spaces of the user are deleted, which the caller has to confirm
     * with {@code resetDocuments}; the spaces themselves stay.
     */
    EmbeddingModelEntity updateEmbeddingModel(UUID userId, Consumer<EmbeddingModelEntity> settings,
                                              boolean resetDocuments);

    long countIndexedChunks(UUID userId);

    SummarizerModelEntity getSummarizerModel(UUID userId);

    SummarizerModelEntity updateSummarizerModel(UUID userId, Consumer<SummarizerModelEntity> settings);
}
