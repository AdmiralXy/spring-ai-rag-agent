package io.github.admiralxy.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "t_embedding_model")
@Getter
@Setter
public class EmbeddingModelEntity {

    public static final short SINGLETON_ID = 1;

    @Id
    @Column(name = "c_id", nullable = false)
    private Short id = SINGLETON_ID;

    @Enumerated(EnumType.STRING)
    @Column(name = "c_provider", nullable = false)
    private EmbeddingModelProvider provider;

    @Column(name = "c_base_url", nullable = false)
    private String baseUrl;

    @Column(name = "c_api_key", nullable = false)
    private String apiKey;

    @Column(name = "c_name", nullable = false)
    private String name;

    @Column(name = "c_dimensions", nullable = false)
    private int dimensions;

    @Column(name = "c_max_document_tokens", nullable = false)
    private int maxDocumentTokens;

    @UpdateTimestamp
    @Column(name = "c_updated_at", nullable = false)
    private Instant updatedAt;
}
