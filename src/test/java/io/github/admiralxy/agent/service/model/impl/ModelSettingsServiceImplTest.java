package io.github.admiralxy.agent.service.model.impl;

import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.entity.EmbeddingModelProvider;
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

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelSettingsServiceImplTest {

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

    @BeforeEach
    void setUp() {
        service = new ModelSettingsServiceImpl(chatModelRepository, embeddingModelRepository,
                summarizerModelRepository, embeddingModelRuntime, ragDocumentRepository);
    }

    @Test
    void recreatesTheVectorStoreWhenDimensionsChange() {
        EmbeddingModelEntity existing = embeddingModel(1536);
        when(embeddingModelRepository.findById(EmbeddingModelEntity.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(embeddingModelRepository.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateEmbeddingModel(entity -> entity.setDimensions(1024));

        verify(ragDocumentRepository).recreate(1024);
        verify(embeddingModelRuntime).invalidate();
    }

    @Test
    void keepsTheVectorStoreWhenDimensionsStayTheSame() {
        EmbeddingModelEntity existing = embeddingModel(1536);
        when(embeddingModelRepository.findById(EmbeddingModelEntity.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(embeddingModelRepository.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateEmbeddingModel(entity -> entity.setName("text-embedding-3-large"));

        verify(ragDocumentRepository, never()).recreate(anyInt());
        verify(embeddingModelRuntime).invalidate();
    }

    private static EmbeddingModelEntity embeddingModel(int dimensions) {
        EmbeddingModelEntity entity = new EmbeddingModelEntity();
        entity.setProvider(EmbeddingModelProvider.OPENAI);
        entity.setBaseUrl("https://api.openai.com");
        entity.setApiKey("test-key");
        entity.setName("text-embedding-3-small");
        entity.setDimensions(dimensions);
        return entity;
    }
}
