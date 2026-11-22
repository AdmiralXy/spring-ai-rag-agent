package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.domain.UploadSession;
import io.github.admiralxy.agent.domain.UploadSessionFile;
import io.github.admiralxy.agent.domain.UploadSessionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadSessionStore {

    void create(UploadSession session, List<UploadSessionFile> files);

    Optional<UploadSession> find(UUID id);

    Optional<UploadSessionFile> findFile(UUID id, int fileIndex);

    List<UploadSessionFile> findFiles(UUID id);

    boolean touch(UUID id, Instant now, Instant expiresAt);

    boolean updateStatus(UUID id, UploadSessionStatus status, Instant expiresAt);

    boolean delete(UUID id);

    boolean deleteIfExpired(UUID id, Instant now);

    List<UUID> findExpiredIds(Instant now, int limit);

    boolean exists(UUID id);
}
