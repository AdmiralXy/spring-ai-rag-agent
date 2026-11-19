package io.github.admiralxy.agent.service;

import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.UploadSessionState;

import java.util.List;
import java.util.UUID;

/**
 * Chunked upload of multiple files that are packed into a single ZIP archive on completion.
 */
public interface UploadSessionService {

    UploadSessionState create(String name, List<UploadFileSpec> files);

    /**
     * Stores one decoded chunk. Re-sending the same chunk overwrites it. Extends the session expiration.
     */
    void putChunk(UUID uploadId, int fileIndex, int chunkIndex, String base64Content);

    UploadSessionState getStatus(UUID uploadId);

    /**
     * Builds the ZIP archive, stores it as a regular file and removes the session.
     */
    StoredFile complete(UUID uploadId);

    void cancel(UUID uploadId);

    /**
     * Removes expired sessions and orphaned temporary directories.
     *
     * @return number of removed sessions
     */
    int cleanupExpired();
}
