package io.github.admiralxy.agent.service.model.impl;

import io.github.admiralxy.agent.entity.ChatModelEntity;
import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.entity.SummarizerModelEntity;
import io.github.admiralxy.agent.repository.ChatModelRepository;
import io.github.admiralxy.agent.repository.EmbeddingModelRepository;
import io.github.admiralxy.agent.repository.RagDocumentRepository;
import io.github.admiralxy.agent.repository.SummarizerModelRepository;
import io.github.admiralxy.agent.service.model.EmbeddingModelRuntime;
import io.github.admiralxy.agent.service.model.ModelSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ModelSettingsServiceImpl implements ModelSettingsService {

    private static final String CHAT_MODEL_NOT_FOUND = "Chat model not found: %s";
    private static final String EMBEDDING_MODEL_MISSING = "Embedding model settings are missing";
    private static final String SUMMARIZER_MODEL_MISSING = "Summarizer model settings are missing";

    private final ChatModelRepository chatModelRepository;
    private final EmbeddingModelRepository embeddingModelRepository;
    private final SummarizerModelRepository summarizerModelRepository;
    private final EmbeddingModelRuntime embeddingModelRuntime;
    private final RagDocumentRepository ragDocumentRepository;

    @Override
    public List<ChatModelEntity> getChatModels() {
        return chatModelRepository.findAllByOrderByPriorityAscCreatedAtAsc();
    }

    @Override
    public ChatModelEntity getChatModel(UUID id) {
        return chatModelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(CHAT_MODEL_NOT_FOUND.formatted(id)));
    }

    @Override
    public ChatModelEntity createChatModel(Consumer<ChatModelEntity> settings) {
        ChatModelEntity entity = new ChatModelEntity();
        settings.accept(entity);
        return chatModelRepository.save(entity);
    }

    @Override
    public ChatModelEntity updateChatModel(UUID id, Consumer<ChatModelEntity> settings) {
        ChatModelEntity entity = getChatModel(id);
        settings.accept(entity);
        return chatModelRepository.save(entity);
    }

    @Override
    public void deleteChatModel(UUID id) {
        chatModelRepository.delete(getChatModel(id));
    }

    @Override
    public EmbeddingModelEntity getEmbeddingModel() {
        return embeddingModelRepository.findById(EmbeddingModelEntity.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(EMBEDDING_MODEL_MISSING));
    }

    @Override
    @Transactional
    public EmbeddingModelEntity updateEmbeddingModel(Consumer<EmbeddingModelEntity> settings) {
        EmbeddingModelEntity entity = getEmbeddingModel();
        int previousDimensions = entity.getDimensions();
        settings.accept(entity);
        EmbeddingModelEntity saved = embeddingModelRepository.save(entity);
        if (previousDimensions != saved.getDimensions()) {
            ragDocumentRepository.recreate(saved.getDimensions());
        }
        embeddingModelRuntime.invalidate();
        return saved;
    }

    @Override
    public SummarizerModelEntity getSummarizerModel() {
        return summarizerModelRepository.findById(SummarizerModelEntity.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(SUMMARIZER_MODEL_MISSING));
    }

    @Override
    public SummarizerModelEntity updateSummarizerModel(Consumer<SummarizerModelEntity> settings) {
        SummarizerModelEntity entity = getSummarizerModel();
        settings.accept(entity);
        return summarizerModelRepository.save(entity);
    }
}
