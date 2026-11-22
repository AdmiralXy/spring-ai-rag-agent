package io.github.admiralxy.agent.service.model.impl;

import io.github.admiralxy.agent.entity.ChatModelEntity;
import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.entity.SummarizerModelEntity;
import io.github.admiralxy.agent.exception.ApiException;
import io.github.admiralxy.agent.repository.ChatModelRepository;
import io.github.admiralxy.agent.repository.EmbeddingModelRepository;
import io.github.admiralxy.agent.repository.RagDocumentRepository;
import io.github.admiralxy.agent.repository.SummarizerModelRepository;
import io.github.admiralxy.agent.service.model.EmbeddingModelRuntime;
import io.github.admiralxy.agent.service.model.ModelSettingsService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ModelSettingsServiceImpl implements ModelSettingsService {

    static final int MIN_DOCUMENT_TOKENS = 16;

    private static final String CHAT_MODEL_NOT_FOUND = "Chat model not found: %s";
    private static final String BUILTIN_READ_ONLY = "The built-in model cannot be changed or deleted";
    private static final String SETTINGS_MISSING = "Model settings of the account are missing";
    private static final String DIMENSIONS_INVALID = "Dimensions must be between 1 and %d";
    private static final String DOCUMENT_TOKENS_INVALID = "Max document tokens must be at least %d";
    private static final String NAME_REQUIRED = "The model name is required";
    private static final String RESET_REQUIRED = "Your spaces contain %d chunks indexed with the current embedding "
            + "model. Vectors of another model, URL or dimension cannot be compared with them, so saving deletes "
            + "the content of all your spaces (the spaces stay). Confirm to continue.";

    private final ChatModelRepository chatModelRepository;
    private final EmbeddingModelRepository embeddingModelRepository;
    private final SummarizerModelRepository summarizerModelRepository;
    private final EmbeddingModelRuntime embeddingModelRuntime;
    private final RagDocumentRepository ragDocumentRepository;

    @Override
    public List<ChatModelEntity> getChatModels(UUID userId) {
        return chatModelRepository.findAvailable(userId);
    }

    @Override
    public ChatModelEntity getChatModel(UUID userId, UUID id) {
        if (id == null) {
            throw ApiException.badRequest(CHAT_MODEL_NOT_FOUND.formatted((Object) null));
        }
        return chatModelRepository.findAvailable(id, userId)
                .orElseThrow(() -> ApiException.notFound(CHAT_MODEL_NOT_FOUND.formatted(id)));
    }

    @Override
    public ChatModelEntity createChatModel(UUID userId, Consumer<ChatModelEntity> settings) {
        ChatModelEntity entity = new ChatModelEntity();
        settings.accept(entity);
        entity.setUserId(userId);
        return chatModelRepository.save(entity);
    }

    @Override
    public ChatModelEntity updateChatModel(UUID userId, UUID id, Consumer<ChatModelEntity> settings) {
        ChatModelEntity entity = ownChatModel(userId, id);
        settings.accept(entity);
        entity.setUserId(userId);
        return chatModelRepository.save(entity);
    }

    @Override
    public void deleteChatModel(UUID userId, UUID id) {
        chatModelRepository.delete(ownChatModel(userId, id));
    }

    @Override
    public EmbeddingModelEntity getEmbeddingModel(UUID userId) {
        return embeddingModelRepository.findById(userId).orElseThrow(() -> new IllegalStateException(SETTINGS_MISSING));
    }

    @Override
    @Transactional
    public EmbeddingModelEntity updateEmbeddingModel(UUID userId, Consumer<EmbeddingModelEntity> settings,
                                                     boolean resetDocuments) {
        EmbeddingModelEntity entity = getEmbeddingModel(userId);
        VectorSpace before = VectorSpace.of(entity);
        settings.accept(entity);
        entity.setUserId(userId);
        validate(entity);
        if (!before.equals(VectorSpace.of(entity))) {
            long chunks = ragDocumentRepository.countOfUser(userId);
            if (chunks > 0 && !resetDocuments) {
                throw ApiException.conflict(RESET_REQUIRED.formatted(chunks));
            }
            if (chunks > 0) {
                ragDocumentRepository.deleteAllOfUser(userId);
            }
        }
        EmbeddingModelEntity saved = embeddingModelRepository.save(entity);
        int dimensions = saved.getDimensions();
        afterCommit(() -> {
            embeddingModelRuntime.invalidate(userId);
            ragDocumentRepository.ensureVectorIndex(dimensions);
        });
        return saved;
    }

    @Override
    public long countIndexedChunks(UUID userId) {
        return ragDocumentRepository.countOfUser(userId);
    }

    @Override
    public SummarizerModelEntity getSummarizerModel(UUID userId) {
        return summarizerModelRepository.findById(userId).orElseThrow(() -> new IllegalStateException(SETTINGS_MISSING));
    }

    @Override
    public SummarizerModelEntity updateSummarizerModel(UUID userId, Consumer<SummarizerModelEntity> settings) {
        SummarizerModelEntity entity = getSummarizerModel(userId);
        settings.accept(entity);
        entity.setUserId(userId);
        return summarizerModelRepository.save(entity);
    }

    private ChatModelEntity ownChatModel(UUID userId, UUID id) {
        ChatModelEntity entity = getChatModel(userId, id);
        if (entity.isBuiltin() || !Objects.equals(entity.getUserId(), userId)) {
            throw ApiException.forbidden(BUILTIN_READ_ONLY);
        }
        return entity;
    }

    private static void validate(EmbeddingModelEntity entity) {
        if (StringUtils.isBlank(entity.getName())) {
            throw ApiException.badRequest(NAME_REQUIRED);
        }
        if (entity.getDimensions() < 1 || entity.getDimensions() > RagDocumentRepository.MAX_DIMENSIONS) {
            throw ApiException.badRequest(DIMENSIONS_INVALID.formatted(RagDocumentRepository.MAX_DIMENSIONS));
        }
        if (entity.getMaxDocumentTokens() < MIN_DOCUMENT_TOKENS) {
            throw ApiException.badRequest(DOCUMENT_TOKENS_INVALID.formatted(MIN_DOCUMENT_TOKENS));
        }
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    /**
     * What makes two vectors comparable; the API key and the chunk size do not matter.
     */
    record VectorSpace(Object provider, String baseUrl, String name, int dimensions) {

        static VectorSpace of(EmbeddingModelEntity entity) {
            return new VectorSpace(entity.getProvider(), StringUtils.trimToEmpty(entity.getBaseUrl()),
                    StringUtils.trimToEmpty(entity.getName()), entity.getDimensions());
        }
    }
}
