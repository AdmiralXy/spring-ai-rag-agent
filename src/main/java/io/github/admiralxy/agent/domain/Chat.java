package io.github.admiralxy.agent.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Chat(
        UUID id,
        String title,
        UUID modelId,
        List<UUID> ragSpaces,
        Instant createdAt,
        Instant updatedAt
) {
}
