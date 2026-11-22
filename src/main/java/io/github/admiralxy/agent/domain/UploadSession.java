package io.github.admiralxy.agent.domain;

import java.time.Instant;
import java.util.UUID;

public record UploadSession(
        UUID id,
        UUID userId,
        String name,
        long chunkSize,
        UploadSessionStatus status,
        Instant createdAt,
        Instant expiresAt,
        byte[] keySalt
) {
    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public UploadSession withStatus(UploadSessionStatus newStatus, Instant newExpiresAt) {
        return new UploadSession(id, userId, name, chunkSize, newStatus, createdAt, newExpiresAt, keySalt);
    }
}
