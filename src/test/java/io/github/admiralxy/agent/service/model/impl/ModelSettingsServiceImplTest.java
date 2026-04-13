package io.github.admiralxy.agent.service.model.impl;

import io.github.admiralxy.agent.entity.EmbeddingModelProviderType;
import io.github.admiralxy.agent.entity.EmbeddingsModelSettingsEntity;
import io.github.admiralxy.agent.repository.ChatModelSettingsRepository;
import io.github.admiralxy.agent.repository.EmbeddingsModelSettingsRepository;
import io.github.admiralxy.agent.repository.SummarizerModelSettingsRepository;
import io.github.admiralxy.agent.service.model.EmbeddingModelRuntime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ModelSettingsServiceImplTest {

    @Mock
    private ChatModelSettingsRepository chatModelRepository;

    @Mock
    private EmbeddingsModelSettingsRepository embeddingsRepository;

    @Mock
    private SummarizerModelSettingsRepository summarizerRepository;

    @Mock
    private EmbeddingModelRuntime embeddingModelRuntime;

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ModelSettingsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ModelSettingsServiceImpl(
                chatModelRepository,
                embeddingsRepository,
                summarizerRepository,
                embeddingModelRuntime,
                jdbcTemplate
        );
    }

    @Test
    void updateEmbeddingsModel_shouldRebuildVectorStore_whenDimensionsChanged() {
        String recreateIndexSql =
                "CREATE INDEX IF NOT EXISTS documents_embedding_idx ON documents "
                        + "USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)";

        EmbeddingsModelSettingsEntity existing = embeddings(1536);
        EmbeddingsModelSettingsEntity update = embeddings(1024);

        when(embeddingsRepository.findById((short) 1)).thenReturn(Optional.of(existing));
        when(embeddingsRepository.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateEmbeddingsModel(update);

        verify(jdbcTemplate).update("DELETE FROM documents");
        verify(jdbcTemplate).update("UPDATE t_spaces SET c_dimensions = ?", 1024);
        verify(jdbcTemplate).execute("DROP INDEX IF EXISTS documents_embedding_idx");
        verify(jdbcTemplate).execute("ALTER TABLE documents ALTER COLUMN embedding TYPE vector(1024)");
        verify(jdbcTemplate).execute(recreateIndexSql);
        verify(embeddingModelRuntime).invalidate();
    }

    @Test
    void updateEmbeddingsModel_shouldSkipIvfflatIndex_whenDimensionsGreaterThan2000() {
        EmbeddingsModelSettingsEntity existing = embeddings(1536);
        EmbeddingsModelSettingsEntity update = embeddings(3072);

        when(embeddingsRepository.findById((short) 1)).thenReturn(Optional.of(existing));
        when(embeddingsRepository.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateEmbeddingsModel(update);

        verify(jdbcTemplate).update("DELETE FROM documents");
        verify(jdbcTemplate).update("UPDATE t_spaces SET c_dimensions = ?", 3072);
        verify(jdbcTemplate).execute("DROP INDEX IF EXISTS documents_embedding_idx");
        verify(jdbcTemplate).execute("ALTER TABLE documents ALTER COLUMN embedding TYPE vector(3072)");
        verify(jdbcTemplate, never()).execute(
                "CREATE INDEX IF NOT EXISTS documents_embedding_idx ON documents "
                        + "USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)"
        );
        verify(embeddingModelRuntime).invalidate();
    }

    @Test
    void updateEmbeddingsModel_shouldSkipRebuild_whenDimensionsNotChanged() {
        EmbeddingsModelSettingsEntity existing = embeddings(1536);
        EmbeddingsModelSettingsEntity update = embeddings(1536);

        when(embeddingsRepository.findById((short) 1)).thenReturn(Optional.of(existing));
        when(embeddingsRepository.save(existing)).thenAnswer(invocation -> invocation.getArgument(0));

        service.updateEmbeddingsModel(update);

        verify(jdbcTemplate, never()).update("DELETE FROM documents");
        verify(jdbcTemplate, never()).execute("DROP INDEX IF EXISTS documents_embedding_idx");
        verify(embeddingModelRuntime, times(1)).invalidate();
    }

    private EmbeddingsModelSettingsEntity embeddings(int dimensions) {
        EmbeddingsModelSettingsEntity entity = new EmbeddingsModelSettingsEntity();
        entity.setId((short) 1);
        entity.setProvider(EmbeddingModelProviderType.OPENAI);
        entity.setBaseUrl("https://api.openai.com");
        entity.setApiKey("test-key");
        entity.setName("text-embedding-3-small");
        entity.setDimensions(dimensions);
        return entity;
    }
}
