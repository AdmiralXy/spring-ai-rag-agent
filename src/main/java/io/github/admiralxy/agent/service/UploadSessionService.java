package io.github.admiralxy.agent.service;

import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.UploadSessionState;

import java.util.List;
import java.util.UUID;

/**
 * Encrypted chunked upload of multiple files that are packed into a single .tar.zst archive on completion.
 */
public interface UploadSessionService {

    /**
     * Creates a session from an encrypted {@link UploadManifest}.
     *
     * @param keySalt  16 random bytes chosen by the client; the session key is derived from it
     * @param manifest encrypted JSON manifest
     */
    UploadSessionState create(byte[] keySalt, EncryptedPayload manifest);

    /**
     * Stores one chunk. Re-sending the same chunk overwrites it. Extends the session expiration.
     */
    void putChunk(UUID uploadId, EncryptedChunk chunk);

    /**
     * Stores several chunks at once; the batch is validated and decrypted completely before anything is written.
     */
    void putChunks(UUID uploadId, List<EncryptedChunk> chunks);

    UploadSessionState getStatus(UUID uploadId);

    /**
     * Builds the .tar.zst archive, stores it as a regular file and removes the session.
     *
     * @param proof encryption of an empty message proving knowledge of the session key
     */
    StoredFile complete(UUID uploadId, EncryptedPayload proof);

    /**
     * @param proof encryption of an empty message proving knowledge of the session key
     */
    void cancel(UUID uploadId, EncryptedPayload proof);

    /**
     * Removes expired sessions and orphaned temporary directories.
     *
     * @return number of removed sessions
     */
    int cleanupExpired();
}
