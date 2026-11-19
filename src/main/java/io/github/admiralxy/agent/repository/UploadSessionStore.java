package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.domain.UploadSession;
import io.github.admiralxy.agent.domain.UploadSessionFile;
import io.github.admiralxy.agent.domain.UploadSessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence of upload sessions and their file lists. Received chunks are tracked on disk, not here.
 */
public interface UploadSessionStore {

    void create(UploadSession session, List<UploadSessionFile> files);

    Optional<UploadSession> find(UUID id);

    Optional<UploadSessionFile> findFile(UUID id, int fileIndex);

    /**
     * @return files ordered by index
     */
    List<UploadSessionFile> findFiles(UUID id);

    /**
     * Extends the expiration of an active, not yet expired session.
     *
     * @return false if the session is missing, expired or not active
     */
    boolean touch(UUID id, Instant now, Instant expiresAt);

    boolean updateStatus(UUID id, UploadSessionStatus status, Instant expiresAt);

    boolean delete(UUID id);

    boolean deleteIfExpired(UUID id, Instant now);

    List<UUID> findExpiredIds(Instant now, int limit);

    boolean exists(UUID id);
}
