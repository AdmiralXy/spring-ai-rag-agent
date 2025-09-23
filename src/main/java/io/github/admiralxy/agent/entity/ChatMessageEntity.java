package io.github.admiralxy.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_chat_messages")
@Getter
@Setter
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "c_id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "c_conversation_id", nullable = false)
    private ConversationEntity conversation;

    @Column(name = "c_role", nullable = false)
    private String role;

    @Column(name = "c_content", columnDefinition = "text", nullable = false)
    private String content;

    @CreationTimestamp
    @Column(name = "c_created_at", nullable = false)
    private Instant createdAt;
}
