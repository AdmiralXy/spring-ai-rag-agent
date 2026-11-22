package io.github.admiralxy.agent.entity;

import io.github.admiralxy.agent.domain.AgentRunStats;
import io.github.admiralxy.agent.domain.ChatAttachment;
import io.github.admiralxy.agent.domain.ChatMessagePart;
import io.github.admiralxy.agent.domain.ChatRole;
import io.github.admiralxy.agent.entity.converter.AgentRunStatsConverter;
import io.github.admiralxy.agent.entity.converter.ChatAttachmentsConverter;
import io.github.admiralxy.agent.entity.converter.ChatMessagePartsConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "t_chat_messages")
@Getter
@Setter
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "c_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "c_chat_id", nullable = false, updatable = false)
    private UUID chatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "c_role", nullable = false)
    private ChatRole role;

    @Column(name = "c_content", nullable = false)
    private String content;

    @Convert(converter = ChatMessagePartsConverter.class)
    @Column(name = "c_parts")
    private List<ChatMessagePart> parts;

    @Convert(converter = ChatAttachmentsConverter.class)
    @Column(name = "c_attachments")
    private List<ChatAttachment> attachments;

    @Convert(converter = AgentRunStatsConverter.class)
    @Column(name = "c_stats")
    private AgentRunStats stats;

    @CreationTimestamp
    @Column(name = "c_created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
