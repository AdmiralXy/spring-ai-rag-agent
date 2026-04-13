package io.github.admiralxy.agent.service.model.impl;

import io.github.admiralxy.agent.entity.ChatModelSettingsEntity;
import io.github.admiralxy.agent.entity.EmbeddingsModelSettingsEntity;
import io.github.admiralxy.agent.entity.SummarizerModelSettingsEntity;
import io.github.admiralxy.agent.repository.ChatModelSettingsRepository;
import io.github.admiralxy.agent.repository.EmbeddingsModelSettingsRepository;
import io.github.admiralxy.agent.repository.SummarizerModelSettingsRepository;
import io.github.admiralxy.agent.service.model.EmbeddingModelRuntime;
import io.github.admiralxy.agent.service.model.ModelSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ModelSettingsServiceImpl implements ModelSettingsService {

    private static final short EMBEDDINGS_SINGLETON_ID = 1;
    private static final short SUMMARIZER_SINGLETON_ID = 1;

    private static final String CHAT_MODEL_NOT_FOUND = "Chat model not found: %s";
    private static final String EMBEDDINGS_MODEL_NOT_FOUND = "Embeddings model settings are missing";
    private static final String SUMMARIZER_MODEL_NOT_FOUND = "Summarizer model settings are missing";

    private static final String SQL_DELETE_DOCUMENTS = "DELETE FROM documents";
    private static final String SQL_UPDATE_SPACES_DIMENSIONS = "UPDATE t_spaces SET c_dimensions = ?";
    private static final String SQL_DROP_DOCUMENTS_VECTOR_INDEX = "DROP INDEX IF EXISTS documents_embedding_idx";
    private static final String SQL_ALTER_DOCUMENTS_VECTOR_DIMENSIONS_TEMPLATE =
            "ALTER TABLE documents ALTER COLUMN embedding TYPE vector(%d)";
    private static final String SQL_RECREATE_DOCUMENTS_VECTOR_INDEX =
            "CREATE INDEX IF NOT EXISTS documents_embedding_idx ON documents "
                    + "USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)";
    private static final int IVFFLAT_MAX_DIMENSIONS = 2000;

    private final ChatModelSettingsRepository chatModelRepository;
    private final EmbeddingsModelSettingsRepository embeddingsRepository;
    private final SummarizerModelSettingsRepository summarizerRepository;
    private final EmbeddingModelRuntime embeddingModelRuntime;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<ChatModelSettingsEntity> getChatModels() {
        return chatModelRepository.findAllByOrderByPriorityAscCreatedAtAsc();
    }

    @Override
    public ChatModelSettingsEntity getChatModel(UUID id) {
        return chatModelRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(CHAT_MODEL_NOT_FOUND.formatted(id)));
    }

    @Override
    public ChatModelSettingsEntity createChatModel(ChatModelSettingsEntity entity) {
        return chatModelRepository.save(entity);
    }

    @Override
    public ChatModelSettingsEntity updateChatModel(UUID id, ChatModelSettingsEntity entity) {
        ChatModelSettingsEntity existing = getChatModel(id);
        existing.setProvider(entity.getProvider());
        existing.setLabel(entity.getLabel());
        existing.setName(entity.getName());
        existing.setBaseUrl(entity.getBaseUrl());
        existing.setApiKey(entity.getApiKey());
        existing.setStreaming(entity.isStreaming());
        existing.setSystemPrompt(entity.getSystemPrompt());
        existing.setPriority(entity.getPriority());
        existing.setTemperature(entity.getTemperature());
        existing.setMaxContextTokens(entity.getMaxContextTokens());
        return chatModelRepository.save(existing);
    }

    @Override
    public void deleteChatModel(UUID id) {
        if (!chatModelRepository.existsById(id)) {
            throw new IllegalArgumentException(CHAT_MODEL_NOT_FOUND.formatted(id));
        }
        chatModelRepository.deleteById(id);
    }

    @Override
    public EmbeddingsModelSettingsEntity getEmbeddingsModel() {
        return embeddingsRepository.findById(EMBEDDINGS_SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(EMBEDDINGS_MODEL_NOT_FOUND));
    }

    @Override
    @Transactional
    public EmbeddingsModelSettingsEntity updateEmbeddingsModel(EmbeddingsModelSettingsEntity entity) {
        EmbeddingsModelSettingsEntity existing = getEmbeddingsModel();
        int previousDimensions = existing.getDimensions();

        existing.setProvider(entity.getProvider());
        existing.setBaseUrl(entity.getBaseUrl());
        existing.setApiKey(entity.getApiKey());
        existing.setName(entity.getName());
        existing.setDimensions(entity.getDimensions());
        existing.setMaxDocumentTokens(entity.getMaxDocumentTokens());

        EmbeddingsModelSettingsEntity saved = embeddingsRepository.save(existing);

        if (previousDimensions != saved.getDimensions()) {
            rebuildVectorStoreForNewDimensions(saved.getDimensions());
        }

        embeddingModelRuntime.invalidate();
        return saved;
    }

    @Override
    public SummarizerModelSettingsEntity getSummarizerModel() {
        return summarizerRepository.findById(SUMMARIZER_SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(SUMMARIZER_MODEL_NOT_FOUND));
    }

    @Override
    public SummarizerModelSettingsEntity updateSummarizerModel(SummarizerModelSettingsEntity entity) {
        SummarizerModelSettingsEntity existing = getSummarizerModel();
        existing.setProvider(entity.getProvider());
        existing.setName(entity.getName());
        existing.setBaseUrl(entity.getBaseUrl());
        existing.setApiKey(entity.getApiKey());
        existing.setSystemPrompt(entity.getSystemPrompt());
        return summarizerRepository.save(existing);
    }

    private void rebuildVectorStoreForNewDimensions(int dimensions) {
        jdbcTemplate.update(SQL_DELETE_DOCUMENTS);
        jdbcTemplate.update(SQL_UPDATE_SPACES_DIMENSIONS, dimensions);
        jdbcTemplate.execute(SQL_DROP_DOCUMENTS_VECTOR_INDEX);
        jdbcTemplate.execute(SQL_ALTER_DOCUMENTS_VECTOR_DIMENSIONS_TEMPLATE.formatted(dimensions));
        if (dimensions <= IVFFLAT_MAX_DIMENSIONS) {
            jdbcTemplate.execute(SQL_RECREATE_DOCUMENTS_VECTOR_INDEX);
            return;
        }

        log.warn(
                "Skip ivfflat index recreation for documents.embedding because dimensions={} exceeds limit={}. "
                        + "Similarity search will work without ANN index until dimensions is reduced.",
                dimensions,
                IVFFLAT_MAX_DIMENSIONS
        );
    }
}
