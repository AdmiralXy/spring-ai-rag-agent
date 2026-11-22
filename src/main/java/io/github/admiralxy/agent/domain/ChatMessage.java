package io.github.admiralxy.agent.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
        UUID id,
        ChatRole role,
        String content,
        List<ChatMessagePart> parts,
        AgentRunStats stats,
        List<ChatAttachment> attachments,
        Instant createdAt
) {
}
