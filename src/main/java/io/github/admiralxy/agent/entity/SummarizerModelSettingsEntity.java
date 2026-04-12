package io.github.admiralxy.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "t_summarizer_model_settings")
@Getter
@Setter
public class SummarizerModelSettingsEntity {

    @Id
    @Column(name = "c_id", nullable = false)
    private Short id;

    @Enumerated(EnumType.STRING)
    @Column(name = "c_provider", nullable = false, length = 32)
    private ChatModelProvider provider;

    @Column(name = "c_name", nullable = false)
    private String name;

    @Column(name = "c_base_url", nullable = false)
    private String baseUrl;

    @Column(name = "c_api_key", nullable = false)
    private String apiKey;

    @Column(name = "c_system_prompt")
    private String systemPrompt;

    @CreationTimestamp
    @Column(name = "c_created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "c_updated_at", nullable = false)
    private Instant updatedAt;
}
