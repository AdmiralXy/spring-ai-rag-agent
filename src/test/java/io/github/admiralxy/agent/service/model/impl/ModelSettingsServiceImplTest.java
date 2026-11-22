package io.github.admiralxy.agent.service.model.impl;

import io.github.admiralxy.agent.entity.ChatModelEntity;
import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.entity.EmbeddingModelProvider;
import io.github.admiralxy.agent.exception.ApiException;
import io.github.admiralxy.agent.repository.ChatModelRepository;
import io.github.admiralxy.agent.repository.EmbeddingModelRepository;
import io.github.admiralxy.agent.repository.RagDocumentRepository;
import io.github.admiralxy.agent.repository.SummarizerModelRepository;
import io.github.admiralxy.agent.service.model.EmbeddingModelRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelSettingsServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private ChatModelRepository chatModelRepository;

    @Mock
    private EmbeddingModelRepository embeddingModelRepository;

    @Mock
    private SummarizerModelRepository summarizerModelRepository;

    @Mock
    private EmbeddingModelRuntime embeddingModelRuntime;

    @Mock
    private RagDocumentRepository ragDocumentRepository;

    private ModelSettingsServiceImpl service;
    private EmbeddingModelEntity existing;

    @BeforeEach
    void setUp() {
        service = new ModelSettingsServiceImpl(chatModelRepository, embeddingModelRepository,
                summarizerModelRepository, embeddingModelRuntime, ragDocumentRepository);
        existing = embeddingModel();
        lenient().when(embeddingModelRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
        lenient().when(embeddingModelRepository.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void asksForConfirmationBeforeDroppingIndexedChunks() {
        when(ragDocumentRepository.countOfUser(USER_ID)).thenReturn(42L);

        ApiException error = assertThrows(ApiException.class,
                () -> service.updateEmbeddingModel(USER_ID, entity -> entity.setDimensions(1024), false));

        assertEquals(HttpStatus.CONFLICT, error.getStatus());
        verify(ragDocumentRepository, never()).deleteAllOfUser(any());
        verify(embeddingModelRepository, never()).save(any());
    }

    @Test
    void dropsTheChunksOfTheUserWhenTheModelChangesAndItIsConfirmed() {
        when(ragDocumentRepository.countOfUser(USER_ID)).thenReturn(42L);

        service.updateEmbeddingModel(USER_ID, entity -> entity.setName("text-embedding-3-large"), true);

        verify(ragDocumentRepository).deleteAllOfUser(USER_ID);
        verify(embeddingModelRuntime).invalidate(USER_ID);
        verify(ragDocumentRepository).ensureVectorIndex(1536);
    }

    @Test
    void keepsTheChunksWhenOnlyTheKeyOrChunkSizeChange() {
        service.updateEmbeddingModel(USER_ID, entity -> {
            entity.setApiKey("another-key");
            entity.setMaxDocumentTokens(2000);
        }, false);

        verify(ragDocumentRepository, never()).countOfUser(any());
        verify(ragDocumentRepository, never()).deleteAllOfUser(any());
        verify(embeddingModelRuntime).invalidate(USER_ID);
    }

    @Test
    void rejectsInvalidDimensions() {
        assertThrows(ApiException.class, () -> service.updateEmbeddingModel(USER_ID,
                entity -> entity.setDimensions(0), true));
    }

    @Test
    void protectsTheBuiltinModel() {
        UUID id = UUID.randomUUID();
        ChatModelEntity builtin = new ChatModelEntity();
        builtin.setId(id);
        builtin.setBuiltin(true);
        when(chatModelRepository.findAvailable(id, USER_ID)).thenReturn(Optional.of(builtin));

        ApiException error = assertThrows(ApiException.class, () -> service.deleteChatModel(USER_ID, id));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(chatModelRepository, never()).delete(any());
    }

    private static EmbeddingModelEntity embeddingModel() {
        EmbeddingModelEntity entity = new EmbeddingModelEntity();
        entity.setUserId(USER_ID);
        entity.setProvider(EmbeddingModelProvider.OPENAI);
        entity.setBaseUrl("https://api.openai.com");
        entity.setApiKey("test-key");
        entity.setName("text-embedding-3-small");
        entity.setDimensions(1536);
        entity.setMaxDocumentTokens(7000);
        return entity;
    }
}
