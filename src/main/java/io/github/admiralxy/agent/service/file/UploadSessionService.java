package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.UploadSessionState;

import java.util.List;
import java.util.UUID;

public interface UploadSessionService {

    UploadSessionState create(UUID userId, byte[] keySalt, EncryptedPayload manifest);

    void putChunk(UUID userId, UUID uploadId, EncryptedChunk chunk);

    void putChunks(UUID userId, UUID uploadId, List<EncryptedChunk> chunks);

    UploadSessionState getStatus(UUID userId, UUID uploadId);

    StoredFile complete(UUID userId, UUID uploadId, EncryptedPayload proof);

    void cancel(UUID userId, UUID uploadId, EncryptedPayload proof);

    int cleanupExpired();
}
