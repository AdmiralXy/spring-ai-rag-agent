package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.UploadSessionState;

import java.util.List;
import java.util.UUID;

public interface UploadSessionService {

    UploadSessionState create(byte[] keySalt, EncryptedPayload manifest);

    void putChunk(UUID uploadId, EncryptedChunk chunk);

    void putChunks(UUID uploadId, List<EncryptedChunk> chunks);

    UploadSessionState getStatus(UUID uploadId);

    StoredFile complete(UUID uploadId, EncryptedPayload proof);

    void cancel(UUID uploadId, EncryptedPayload proof);

    int cleanupExpired();
}
