package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.domain.UploadSession;
import io.github.admiralxy.agent.domain.UploadSessionFile;
import io.github.admiralxy.agent.domain.UploadSessionStatus;
import io.github.admiralxy.agent.repository.UploadSessionStore;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Thread-safe in-memory {@link UploadSessionStore} with the same semantics as the JDBC implementation.
 */
class InMemoryUploadSessionStore implements UploadSessionStore {

    private final Map<UUID, UploadSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, List<UploadSessionFile>> files = new ConcurrentHashMap<>();

    @Override
    public void create(UploadSession session, List<UploadSessionFile> sessionFiles) {
        files.put(session.id(), List.copyOf(sessionFiles));
        sessions.put(session.id(), session);
    }

    @Override
    public Optional<UploadSession> find(UUID id) {
        return Optional.ofNullable(sessions.get(id));
    }

    @Override
    public Optional<UploadSessionFile> findFile(UUID id, int fileIndex) {
        if (!sessions.containsKey(id)) {
            return Optional.empty();
        }
        return files.getOrDefault(id, List.of()).stream().filter(f -> f.index() == fileIndex).findFirst();
    }

    @Override
    public List<UploadSessionFile> findFiles(UUID id) {
        if (!sessions.containsKey(id)) {
            return List.of();
        }
        return files.getOrDefault(id, List.of()).stream().sorted(Comparator.comparingInt(UploadSessionFile::index)).toList();
    }

    @Override
    public boolean touch(UUID id, Instant now, Instant expiresAt) {
        AtomicBoolean touched = new AtomicBoolean();
        sessions.computeIfPresent(id, (key, s) -> {
            if (s.status() != UploadSessionStatus.ACTIVE || !s.expiresAt().isAfter(now)) {
                return s;
            }
            touched.set(true);
            return new UploadSession(s.id(), s.name(), s.chunkSize(), s.status(), s.createdAt(), expiresAt);
        });
        return touched.get();
    }

    @Override
    public boolean updateStatus(UUID id, UploadSessionStatus status, Instant expiresAt) {
        return sessions.computeIfPresent(id, (key, s) ->
                new UploadSession(s.id(), s.name(), s.chunkSize(), status, s.createdAt(), expiresAt)) != null;
    }

    @Override
    public boolean delete(UUID id) {
        files.remove(id);
        return sessions.remove(id) != null;
    }

    @Override
    public boolean deleteIfExpired(UUID id, Instant now) {
        AtomicBoolean deleted = new AtomicBoolean();
        sessions.computeIfPresent(id, (key, s) -> {
            if (s.expiresAt().isAfter(now)) {
                return s;
            }
            deleted.set(true);
            return null;
        });
        if (deleted.get()) {
            files.remove(id);
        }
        return deleted.get();
    }

    @Override
    public List<UUID> findExpiredIds(Instant now, int limit) {
        return sessions.values().stream()
                .filter(s -> !s.expiresAt().isAfter(now))
                .map(UploadSession::id)
                .limit(limit)
                .toList();
    }

    @Override
    public boolean exists(UUID id) {
        return sessions.containsKey(id);
    }
}
