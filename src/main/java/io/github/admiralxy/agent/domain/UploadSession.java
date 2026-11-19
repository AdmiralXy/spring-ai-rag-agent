package io.github.admiralxy.agent.domain;

import java.time.Instant;
import java.util.UUID;

public record UploadSession(
        UUID id,
        String name,
        long chunkSize,
        UploadSessionStatus status,
        Instant createdAt,
        Instant expiresAt
) {

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
