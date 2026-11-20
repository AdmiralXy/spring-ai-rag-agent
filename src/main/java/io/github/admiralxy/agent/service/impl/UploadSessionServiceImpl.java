package io.github.admiralxy.agent.service.impl;

import io.airlift.compress.v3.zstd.ZstdJavaOutputStream;
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
import io.github.admiralxy.agent.service.EncryptedChunk;
import io.github.admiralxy.agent.service.EncryptedPayload;
import io.github.admiralxy.agent.service.FileStorageService;
import io.github.admiralxy.agent.service.UploadFileSpec;
import io.github.admiralxy.agent.service.UploadManifest;
import io.github.admiralxy.agent.service.UploadSessionService;
import io.github.admiralxy.agent.service.file.FileNameRules;
import io.github.admiralxy.agent.service.file.UploadChunkStore;
import io.github.admiralxy.agent.service.file.UploadCrypto;
import io.github.admiralxy.agent.service.file.UploadPathRules;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

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
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

/**
 * Encrypted chunked folder uploads.
 *
 * <p>Concurrency: each session is guarded by a read/write lock. Chunk uploads take the read lock only to
 * re-check the session and atomically publish already decrypted and written chunks, so they run in parallel.
 * {@code complete}, {@code cancel} and cleanup take the write lock, so they never interleave with chunks
 * being published. Chunks live on local disk, so the application is expected to run as a single instance
 * (or with sticky routing by upload id).
 *
 * <p>Confidentiality: file names and paths only travel encrypted. Responses and error messages refer to
 * files by index.
 */
@Slf4j
@Service
public class UploadSessionServiceImpl implements UploadSessionService {

    private static final int LOCK_STRIPES = 64;
    private static final int CLEANUP_BATCH_SIZE = 100;
    private static final int ARCHIVE_BUFFER_SIZE = 64 * 1024;
    private static final String ACTION_COMPLETE = "complete";
    private static final String ACTION_CANCEL = "cancel";

    private final UploadSessionStore store;
    private final UploadChunkStore chunkStore;
    private final UploadCrypto crypto;
    private final FileStorageService fileStorageService;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final long chunkSize;
    private final long maxTotalSize;
    private final int maxFiles;
    private final int maxBatchChunks;
    private final Duration ttl;
    private final ReadWriteLock[] locks = new ReadWriteLock[LOCK_STRIPES];

    @SuppressWarnings("checkstyle:ParameterNumber")
    public UploadSessionServiceImpl(
            UploadSessionStore store,
            UploadChunkStore chunkStore,
            UploadCrypto crypto,
            FileStorageService fileStorageService,
            JsonMapper jsonMapper,
            PlatformTransactionManager transactionManager,
            Clock clock,
            FilesProperties properties
    ) {
        this.store = store;
        this.chunkStore = chunkStore;
        this.crypto = crypto;
        this.fileStorageService = fileStorageService;
        this.jsonMapper = jsonMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        FilesProperties.Uploads uploads = properties.getUploads();
        this.chunkSize = uploads.getChunkSize().toBytes();
        this.maxTotalSize = uploads.getMaxTotalSize().toBytes();
        this.maxFiles = uploads.getMaxFiles();
        this.maxBatchChunks = uploads.getMaxBatchChunks();
        this.ttl = uploads.getTtl();
        for (int i = 0; i < LOCK_STRIPES; i++) {
            locks[i] = new ReentrantReadWriteLock();
        }
    }

    @Override
    public UploadSessionState create(byte[] keySalt, EncryptedPayload encryptedManifest) {
        byte[] plaintext = crypto.decrypt(keySalt, encryptedManifest.iv(), encryptedManifest.data(), UploadCrypto.createAad());
        UploadManifest manifest;
        try {
            manifest = jsonMapper.readValue(plaintext, UploadManifest.class);
        } catch (JacksonException e) {
            throw new InvalidFileException("Decrypted upload manifest is not valid JSON");
        }
        if (manifest == null) {
            throw new InvalidFileException("Decrypted upload manifest is empty");
        }

        String archiveName = FileNameRules.normalizeName(manifest.name());
        List<UploadFileSpec> specs = manifest.files();
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
                UUID.randomUUID(), archiveName, chunkSize, UploadSessionStatus.ACTIVE, now, now.plus(ttl), keySalt.clone());
        store.create(session, files);
        return new UploadSessionState(session, files, Map.of());
    }

    @Override
    public void putChunk(UUID uploadId, EncryptedChunk chunk) {
        storeChunks(uploadId, List.of(chunk), false);
    }

    @Override
    public void putChunks(UUID uploadId, List<EncryptedChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new InvalidFileException("Field 'chunks' must not be empty");
        }
        if (chunks.size() > maxBatchChunks) {
            throw new LimitExceededException("Batch contains " + chunks.size() + " chunks, the maximum is " + maxBatchChunks);
        }
        storeChunks(uploadId, chunks, true);
    }

    private void storeChunks(UUID uploadId, List<EncryptedChunk> chunks, boolean batch) {
        UploadSession session = store.find(uploadId).orElseThrow(() -> notFound(uploadId));
        checkActive(session, clock.instant());
        IntFunction<Optional<UploadSessionFile>> fileLookup = fileLookup(uploadId, batch);

        // Validate and decrypt everything first: a batch is either stored completely or not at all.
        List<DecryptedChunk> decrypted = new ArrayList<>(chunks.size());
        Set<Long> seen = new HashSet<>();
        for (int k = 0; k < chunks.size(); k++) {
            String prefix = batch ? "chunks[" + k + "]: " : "";
            EncryptedChunk chunk = chunks.get(k);
            if (chunk == null) {
                throw new InvalidFileException(prefix + "chunk is required");
            }
            int fileIndex = chunk.fileIndex();
            int chunkIndex = chunk.chunkIndex();
            UploadSessionFile file = fileLookup.apply(fileIndex).orElseThrow(() ->
                    new UploadNotFoundException(prefix + "File index " + fileIndex + " not found in upload " + uploadId));
            if (chunkIndex < 0 || chunkIndex >= file.chunkCount()) {
                throw new UploadNotFoundException(prefix + "Chunk index " + chunkIndex + " not found: file " + fileIndex
                        + " has " + file.chunkCount() + " chunk(s)");
            }
            if (!seen.add(((long) fileIndex << 32) | chunkIndex)) {
                throw new InvalidFileException(prefix + "Chunk " + chunkIndex + " of file " + fileIndex + " is repeated in the batch");
            }
            long expected = expectedChunkSize(session, file, chunkIndex);
            int actual = chunk.payload().data().length - UploadCrypto.TAG_LENGTH;
            if (actual != expected) {
                throw new InvalidFileException(prefix + "Chunk " + chunkIndex + " of file " + fileIndex + " must be " + expected
                        + " bytes before encryption, got " + Math.max(actual, 0));
            }
            byte[] content = crypto.decrypt(session.keySalt(), chunk.payload().iv(), chunk.payload().data(),
                    UploadCrypto.chunkAad(uploadId, fileIndex, chunkIndex));
            decrypted.add(new DecryptedChunk(fileIndex, chunkIndex, content));
        }

        // Write outside the lock; only the atomic publish is serialized against complete/cancel/cleanup.
        List<Path> temps = new ArrayList<>(decrypted.size());
        try {
            for (DecryptedChunk chunk : decrypted) {
                temps.add(io(() -> chunkStore.writeTemp(uploadId, chunk.fileIndex(), chunk.chunkIndex(), chunk.content())));
            }
            Lock lock = lockFor(uploadId).readLock();
            lock.lock();
            try {
                Instant now = clock.instant();
                if (!store.touch(uploadId, now, now.plus(ttl))) {
                    throw unavailable(uploadId, now);
                }
                for (int i = 0; i < decrypted.size(); i++) {
                    DecryptedChunk chunk = decrypted.get(i);
                    Path tmp = temps.get(i);
                    io(() -> {
                        chunkStore.commit(tmp, uploadId, chunk.fileIndex(), chunk.chunkIndex());
                        return null;
                    });
                }
            } finally {
                lock.unlock();
            }
        } finally {
            temps.forEach(chunkStore::deleteQuietly);
        }
    }

    private IntFunction<Optional<UploadSessionFile>> fileLookup(UUID uploadId, boolean batch) {
        if (!batch) {
            return index -> store.findFile(uploadId, index);
        }
        Map<Integer, UploadSessionFile> files = new HashMap<>();
        for (UploadSessionFile file : store.findFiles(uploadId)) {
            files.put(file.index(), file);
        }
        return index -> Optional.ofNullable(files.get(index));
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
    public StoredFile complete(UUID uploadId, EncryptedPayload proof) {
        UploadSession session;
        List<UploadSessionFile> files;
        Lock writeLock = lockFor(uploadId).writeLock();
        writeLock.lock();
        try {
            Instant now = clock.instant();
            session = store.find(uploadId).orElseThrow(() -> notFound(uploadId));
            verifyProof(session, proof, ACTION_COMPLETE);
            checkActive(session, now);
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
    public void cancel(UUID uploadId, EncryptedPayload proof) {
        Lock writeLock = lockFor(uploadId).writeLock();
        writeLock.lock();
        try {
            UploadSession session = store.find(uploadId).orElseThrow(() -> notFound(uploadId));
            verifyProof(session, proof, ACTION_CANCEL);
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

    private void verifyProof(UploadSession session, EncryptedPayload proof, String action) {
        if (proof == null) {
            throw new InvalidFileException("Upload proof is required");
        }
        byte[] plaintext = crypto.decrypt(session.keySalt(), proof.iv(), proof.data(), UploadCrypto.actionAad(action, session.id()));
        if (plaintext.length != 0) {
            throw new InvalidFileException("Upload proof must encrypt an empty message");
        }
    }

    private static void checkActive(UploadSession session, Instant now) {
        if (session.isExpired(now)) {
            throw new UploadExpiredException(session.id());
        }
        if (session.status() == UploadSessionStatus.COMPLETING) {
            throw new UploadConflictException("Upload " + session.id() + " is being completed");
        }
    }

    private RuntimeException unavailable(UUID uploadId, Instant now) {
        Optional<UploadSession> session = store.find(uploadId);
        if (session.isEmpty()) {
            return notFound(uploadId);
        }
        try {
            checkActive(session.get(), now);
        } catch (RuntimeException e) {
            return e;
        }
        return new UploadConflictException("Upload " + uploadId + " is not accepting chunks");
    }

    private void checkAllChunksReceived(List<UploadSessionFile> files, Map<Integer, SortedSet<Integer>> received) {
        long missing = 0;
        int firstMissingFile = -1;
        int firstMissingChunk = -1;
        for (UploadSessionFile file : files) {
            Set<Integer> got = received.getOrDefault(file.index(), Collections.emptySortedSet());
            for (int chunk = 0; chunk < file.chunkCount(); chunk++) {
                if (!got.contains(chunk)) {
                    missing++;
                    if (firstMissingFile < 0) {
                        firstMissingFile = file.index();
                        firstMissingChunk = chunk;
                    }
                }
            }
        }
        if (missing > 0) {
            throw new UploadConflictException(missing + " chunk(s) are missing; first missing: file " + firstMissingFile
                    + " chunk " + firstMissingChunk);
        }
    }

    /**
     * Streams chunks into a {@code .tar.zst} archive (POSIX tar with PAX headers for long and non-ASCII names,
     * compressed with pure-Java zstd). Nothing is held in memory besides stream buffers.
     */
    private Path buildArchive(UUID uploadId, List<UploadSessionFile> files) throws IOException {
        Path archive = chunkStore.archiveTempPath(uploadId);
        Date modified = Date.from(clock.instant());
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(archive), ARCHIVE_BUFFER_SIZE);
             OutputStream zstd = new ZstdJavaOutputStream(out);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(zstd, StandardCharsets.UTF_8.name())) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            tar.setAddPaxHeadersForNonAsciiNames(true);
            Set<String> directories = new HashSet<>();
            for (UploadSessionFile file : files) {
                addParentDirectories(tar, file.path(), directories, modified);
                TarArchiveEntry entry = tarEntry(file.path(), modified);
                entry.setSize(file.size());
                tar.putArchiveEntry(entry);
                for (int chunk = 0; chunk < file.chunkCount(); chunk++) {
                    Files.copy(chunkStore.chunkPath(uploadId, file.index(), chunk), tar);
                }
                tar.closeArchiveEntry();
            }
            tar.finish();
        } catch (IOException | RuntimeException e) {
            chunkStore.deleteQuietly(archive);
            throw e;
        }
        return archive;
    }

    private static void addParentDirectories(TarArchiveOutputStream tar, String path, Set<String> directories, Date modified)
            throws IOException {
        for (int slash = path.indexOf('/'); slash > 0; slash = path.indexOf('/', slash + 1)) {
            String directory = path.substring(0, slash + 1);
            if (directories.add(directory)) {
                tar.putArchiveEntry(tarEntry(directory, modified));
                tar.closeArchiveEntry();
            }
        }
    }

    /**
     * Entry with neutral ownership: TarArchiveEntry would otherwise record the server's OS user name.
     */
    private static TarArchiveEntry tarEntry(String name, Date modified) {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setUserName("");
        entry.setGroupName("");
        entry.setUserId(0);
        entry.setGroupId(0);
        entry.setModTime(modified);
        return entry;
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

    private record DecryptedChunk(int fileIndex, int chunkIndex, byte[] content) {
    }
}
