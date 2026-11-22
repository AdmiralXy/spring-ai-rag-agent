package io.github.admiralxy.agent.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "t_chats")
@Getter
@Setter
public class ChatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "c_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "c_title", nullable = false)
    private String title;

    @Column(name = "c_title_generated", nullable = false)
    private boolean titleGenerated;

    @Column(name = "c_model_id")
    private UUID modelId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "t_chat_spaces", joinColumns = @JoinColumn(name = "c_chat_id"))
    @Column(name = "c_space_id", nullable = false)
    private Set<UUID> spaceIds = new LinkedHashSet<>();

    @CreationTimestamp
    @Column(name = "c_created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "c_updated_at", nullable = false)
    private Instant updatedAt;
}
