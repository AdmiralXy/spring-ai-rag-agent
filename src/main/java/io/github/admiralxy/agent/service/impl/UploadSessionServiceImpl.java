package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.config.properties.FilesProperties;
import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.UploadSession;
import io.github.admiralxy.agent.domain.UploadSessionFile;
import io.github.admiralxy.agent.domain.UploadSessionState;
import io.github.admiralxy.agent.domain.UploadSessionStatus;
import io.github.admiralxy.agent.exception.InvalidFileException;
import io.github.admiralxy.agent.exception.LimitExceededException;
import io.github.admiralxy.agent.exception.UploadConflictException;
import io.github.admiralxy.agent.exception.UploadExpiredException;
import io.github.admiralxy.agent.exception.UploadNotFoundException;
import io.github.admiralxy.agent.repository.UploadSessionStore;
import io.github.admiralxy.agent.service.FileStorageService;
import io.github.admiralxy.agent.service.UploadFileSpec;
import io.github.admiralxy.agent.service.UploadSessionService;
import io.github.admiralxy.agent.service.file.Base64Content;
import io.github.admiralxy.agent.service.file.FileNameRules;
import io.github.admiralxy.agent.service.file.UploadChunkStore;
import io.github.admiralxy.agent.service.file.UploadPathRules;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Chunked folder uploads.
 *
 * <p>Concurrency: each session is guarded by a read/write lock. Chunk uploads take the read lock only to
 * re-check the session and atomically publish an already written chunk, so they run in parallel.
 * {@code complete}, {@code cancel} and cleanup take the write lock, so they never interleave with a chunk
 * being published. Chunks live on local disk, so the application is expected to run as a single instance
 * (or with sticky routing by upload id).
 */
@Slf4j
@Service
public class UploadSessionServiceImpl implements UploadSessionService {

    private static final int LOCK_STRIPES = 64;
    private static final int CLEANUP_BATCH_SIZE = 100;
    private static final int ZIP_BUFFER_SIZE = 64 * 1024;

    private final UploadSessionStore store;
    private final UploadChunkStore chunkStore;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final long chunkSize;
    private final long maxTotalSize;
    private final int maxFiles;
    private final Duration ttl;
    private final ReadWriteLock[] locks = new ReadWriteLock[LOCK_STRIPES];

    public UploadSessionServiceImpl(
            UploadSessionStore store,
            UploadChunkStore chunkStore,
            FileStorageService fileStorageService,
            PlatformTransactionManager transactionManager,
            Clock clock,
            FilesProperties properties
    ) {
        this.store = store;
        this.chunkStore = chunkStore;
        this.fileStorageService = fileStorageService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        FilesProperties.Uploads uploads = properties.getUploads();
        this.chunkSize = uploads.getChunkSize().toBytes();
        this.maxTotalSize = uploads.getMaxTotalSize().toBytes();
        this.maxFiles = uploads.getMaxFiles();
        this.ttl = uploads.getTtl();
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new ReentrantReadWriteLock();
        }
    }

    @Override
    public UploadSessionState create(String name, List<UploadFileSpec> specs) {
        String archiveName = FileNameRules.normalizeName(name);
        if (specs == null || specs.isEmpty()) {
            throw new InvalidFileException("Field 'files' must not be empty");
        }
        if (specs.size() > maxFiles) {
            throw new LimitExceededException("Upload contains " + specs.size() + " files, the maximum is " + maxFiles);
        }

        List<UploadSessionFile> files = new ArrayList<>(specs.size());
        List<String> paths = new ArrayList<>(specs.size());
        long totalSize = 0;
        for (int i = 0; i < specs.size(); i++) {
            UploadFileSpec spec = specs.get(i);
            if (spec == null) {
                throw new InvalidFileException("Field 'files[" + i + "]' is required");
            }
            String path = UploadPathRules.normalize(spec.path(), i);
            Long size = spec.size();
            if (size == null || size < 0) {
                throw new InvalidFileException("Field 'files[" + i + "].size' must be >= 0");
            }
            if (size > maxTotalSize - totalSize) {
                throw new LimitExceededException("Total upload size exceeds the maximum of " + maxTotalSize + " bytes");
            }
            totalSize += size;
            int chunkCount = Math.toIntExact((size + chunkSize - 1) / chunkSize);
            files.add(new UploadSessionFile(i, path, size, chunkCount));
            paths.add(path);
        }
        UploadPathRules.checkConflicts(paths);

        Instant now = clock.instant();
        UploadSession session = new UploadSession(
                UUID.randomUUID(), archiveName, chunkSize, UploadSessionStatus.ACTIVE, now, now.plus(ttl));
        store.create(session, files);
        return new UploadSessionState(session, files, Map.of());
    }

    @Override
    public void putChunk(UUID uploadId, int fileIndex, int chunkIndex, String base64Content) {
        String payload = Base64Content.payload(base64Content);
        UploadSession session = requireActive(uploadId, clock.instant());
        UploadSessionFile file = store.findFile(uploadId, fileIndex)
                .orElseThrow(() -> new UploadNotFoundException("File index " + fileIndex + " not found in upload " + uploadId));
        if (chunkIndex < 0 || chunkIndex >= file.chunkCount()) {
            throw new UploadNotFoundException("Chunk index " + chunkIndex + " not found: file " + fileIndex
                    + " has " + file.chunkCount() + " chunk(s)");
        }

        long expected = expectedChunkSize(session, file, chunkIndex);
        if (Base64Content.estimateDecodedSize(payload) > expected + 2) {
            throw chunkSizeMismatch(file, chunkIndex, expected, Base64Content.estimateDecodedSize(payload));
        }
        byte[] content = Base64Content.decode(payload);
        if (content.length != expected) {
            throw chunkSizeMismatch(file, chunkIndex, expected, content.length);
        }

        // Write outside the lock; only the atomic publish is serialized against complete/cancel/cleanup.
        Path tmp = io(() -> chunkStore.writeTemp(uploadId, fileIndex, chunkIndex, content));
        Lock lock = lockFor(uploadId).readLock();
        lock.lock();
        try {
            Instant now = clock.instant();
            if (!store.touch(uploadId, now, now.plus(ttl))) {
                throw unavailable(uploadId, now);
            }
            io(() -> {
                chunkStore.commit(tmp, uploadId, fileIndex, chunkIndex);
                return null;
            });
        } finally {
            lock.unlock();
            chunkStore.deleteQuietly(tmp);
        }
    }

    @Override
    public UploadSessionState getStatus(UUID uploadId) {
        UploadSession session = store.find(uploadId).orElseThrow(() -> notFound(uploadId));
        if (session.isExpired(clock.instant())) {
            throw new UploadExpiredException(uploadId);
        }
        List<UploadSessionFile> files = store.findFiles(uploadId);
        Map<Integer, SortedSet<Integer>> received = io(() -> chunkStore.listReceived(uploadId));
        return new UploadSessionState(session, files, received);
    }

    @Override
    public StoredFile complete(UUID uploadId) {
        UploadSession session;
        List<UploadSessionFile> files;
        Lock writeLock = lockFor(uploadId).writeLock();
        writeLock.lock();
        try {
            Instant now = clock.instant();
            session = requireActive(uploadId, now);
            files = store.findFiles(uploadId);
            checkAllChunksReceived(files, io(() -> chunkStore.listReceived(uploadId)));
            store.updateStatus(uploadId, UploadSessionStatus.COMPLETING, now.plus(ttl));
        } finally {
            writeLock.unlock();
        }

        Path archive = null;
        try {
            archive = io(() -> buildArchive(uploadId, files));
            Path builtArchive = archive;
            StoredFile stored = transactionTemplate.execute(status -> {
                StoredFile file = fileStorageService.storeArchive(session.name(), builtArchive);
                if (!store.delete(uploadId)) {
                    // Cancelled while the archive was being built; rollback removes the stored archive.
                    throw notFound(uploadId);
                }
                return file;
            });
            chunkStore.deleteSession(uploadId);
            return stored;
        } catch (RuntimeException e) {
            revertToActive(uploadId);
            throw e;
        } finally {
            if (archive != null) {
                chunkStore.deleteQuietly(archive);
            }
        }
    }

    @Override
    public void cancel(UUID uploadId) {
        Lock writeLock = lockFor(uploadId).writeLock();
        writeLock.lock();
        try {
            if (!store.delete(uploadId)) {
                throw notFound(uploadId);
            }
        } finally {
            writeLock.unlock();
        }
        chunkStore.deleteSession(uploadId);
    }

    @Override
    public int cleanupExpired() {
        Instant now = clock.instant();
        int removed = 0;
        List<UUID> expired;
        do {
            expired = store.findExpiredIds(now, CLEANUP_BATCH_SIZE);
            int removedInBatch = 0;
            for (UUID id : expired) {
                if (deleteWithLock(id, () -> store.deleteIfExpired(id, now))) {
                    removedInBatch++;
                }
            }
            removed += removedInBatch;
            if (removedInBatch == 0) {
                break;
            }
        } while (expired.size() == CLEANUP_BATCH_SIZE);

        // Directories left behind by a crash or by a chunk written while its session was being removed.
        for (UUID id : io(chunkStore::listSessionIds)) {
            deleteWithLock(id, () -> !store.exists(id));
        }
        return removed;
    }

    private boolean deleteWithLock(UUID id, BooleanSupplier deleteCondition) {
        boolean deleted;
        Lock writeLock = lockFor(id).writeLock();
        writeLock.lock();
        try {
            deleted = deleteCondition.getAsBoolean();
        } finally {
            writeLock.unlock();
        }
        if (deleted) {
            chunkStore.deleteSession(id);
        }
        return deleted;
    }

    private UploadSession requireActive(UUID uploadId, Instant now) {
        UploadSession session = store.find(uploadId).orElseThrow(() -> notFound(uploadId));
        if (session.isExpired(now)) {
            throw new UploadExpiredException(uploadId);
        }
        if (session.status() == UploadSessionStatus.COMPLETING) {
            throw new UploadConflictException("Upload " + uploadId + " is being completed");
        }
        return session;
    }

    private RuntimeException unavailable(UUID uploadId, Instant now) {
        try {
            requireActive(uploadId, now);
        } catch (RuntimeException e) {
            return e;
        }
        return new UploadConflictException("Upload " + uploadId + " is not accepting chunks");
    }

    private void checkAllChunksReceived(List<UploadSessionFile> files, Map<Integer, SortedSet<Integer>> received) {
        long missing = 0;
        String firstMissingPath = null;
        int firstMissingChunk = -1;
        for (UploadSessionFile file : files) {
            Set<Integer> got = received.getOrDefault(file.index(), Collections.emptySortedSet());
            for (int chunk = 0; chunk < file.chunkCount(); chunk++) {
                if (!got.contains(chunk)) {
                    missing++;
                    if (firstMissingPath == null) {
                        firstMissingPath = file.path();
                        firstMissingChunk = chunk;
                    }
                }
            }
        }
        if (missing > 0) {
            throw new UploadConflictException(missing + " chunk(s) are missing; first missing: '" + firstMissingPath
                    + "' chunk " + firstMissingChunk);
        }
    }

    private Path buildArchive(UUID uploadId, List<UploadSessionFile> files) throws IOException {
        Path archive = chunkStore.archiveTempPath(uploadId);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(archive), ZIP_BUFFER_SIZE);
             ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            // UTF-8 charset makes ZipOutputStream set the language encoding flag (bit 11) on every entry.
            Set<String> directories = new HashSet<>();
            for (UploadSessionFile file : files) {
                addParentDirectories(zip, file.path(), directories);
                zip.putNextEntry(new ZipEntry(file.path()));
                for (int chunk = 0; chunk < file.chunkCount(); chunk++) {
                    Files.copy(chunkStore.chunkPath(uploadId, file.index(), chunk), zip);
                }
                zip.closeEntry();
            }
        } catch (IOException | RuntimeException e) {
            chunkStore.deleteQuietly(archive);
            throw e;
        }
        return archive;
    }

    private static void addParentDirectories(ZipOutputStream zip, String path, Set<String> directories) throws IOException {
        for (int slash = path.indexOf('/'); slash > 0; slash = path.indexOf('/', slash + 1)) {
            String directory = path.substring(0, slash + 1);
            if (directories.add(directory)) {
                zip.putNextEntry(new ZipEntry(directory));
                zip.closeEntry();
            }
        }
    }

    private void revertToActive(UUID uploadId) {
        Lock writeLock = lockFor(uploadId).writeLock();
        writeLock.lock();
        try {
            store.find(uploadId)
                    .filter(s -> s.status() == UploadSessionStatus.COMPLETING)
                    .ifPresent(s -> store.updateStatus(uploadId, UploadSessionStatus.ACTIVE, clock.instant().plus(ttl)));
        } catch (RuntimeException e) {
            log.warn("Failed to revert upload {} to ACTIVE", uploadId, e);
        } finally {
            writeLock.unlock();
        }
    }

    private static long expectedChunkSize(UploadSession session, UploadSessionFile file, int chunkIndex) {
        if (chunkIndex < file.chunkCount() - 1) {
            return session.chunkSize();
        }
        return file.size() - session.chunkSize() * (file.chunkCount() - 1);
    }

    private static InvalidFileException chunkSizeMismatch(UploadSessionFile file, int chunkIndex, long expected, long actual) {
        return new InvalidFileException("Chunk " + chunkIndex + " of '" + file.path() + "' must be " + expected
                + " bytes, got " + actual);
    }

    private static UploadNotFoundException notFound(UUID uploadId) {
        return new UploadNotFoundException("Upload session not found: " + uploadId);
    }

    private ReadWriteLock lockFor(UUID uploadId) {
        return locks[Math.floorMod(uploadId.hashCode(), LOCK_STRIPES)];
    }

    private static <T> T io(IoSupplier<T> action) {
        try {
            return action.get();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws IOException;
    }
}
