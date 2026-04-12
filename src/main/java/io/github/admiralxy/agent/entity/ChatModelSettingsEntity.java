package io.github.admiralxy.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_chat_model_settings")
@Getter
@Setter
public class ChatModelSettingsEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "c_id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "c_provider", nullable = false, length = 32)
    private ChatModelProvider provider;

    @Column(name = "c_label", nullable = false)
    private String label;

    @Column(name = "c_name", nullable = false)
    private String name;

    @Column(name = "c_base_url", nullable = false)
    private String baseUrl;

    @Column(name = "c_api_key", nullable = false)
    private String apiKey;

    @Column(name = "c_streaming", nullable = false)
    private boolean streaming;

    @Column(name = "c_system_prompt")
    private String systemPrompt;

    @Column(name = "c_priority", nullable = false)
    private int priority;

    @Column(name = "c_temperature", nullable = false)
    private double temperature;

    @Column(name = "c_max_context_tokens", nullable = false)
    private int maxContextTokens;

    @CreationTimestamp
    @Column(name = "c_created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "c_updated_at", nullable = false)
    private Instant updatedAt;
}
