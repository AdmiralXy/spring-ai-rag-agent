package io.github.admiralxy.agent.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "t_conversations")
@Getter
@Setter
public class ConversationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "c_id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "c_title", nullable = false)
    private String title;

    @Column(name = "c_rag_space")
    private String ragSpace;

    @CreationTimestamp
    @Column(name = "c_created_at", nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "c_updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ChatMessageEntity> messages = new ArrayList<>();
}
