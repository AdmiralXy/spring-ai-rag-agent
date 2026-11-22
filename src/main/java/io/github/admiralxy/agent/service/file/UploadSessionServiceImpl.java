package io.github.admiralxy.agent.service.file;

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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntFunction;

@Slf4j
@Service
public class UploadSessionServiceImpl implements UploadSessionService {

    private static final int LOCK_STRIPES = 64;
    private static final int CLEANUP_BATCH_SIZE = 100;
    private static final int FILE_INDEX_SHIFT = 32;
    private static final String ACTION_COMPLETE = "complete";
    private static final String ACTION_CANCEL = "cancel";
    private static final String BATCH_PREFIX = "chunks[%d]: ";
    private static final String MANIFEST_NOT_JSON = "Decrypted upload manifest is not valid JSON";
    private static final String MANIFEST_EMPTY = "Decrypted upload manifest is empty";
    private static final String FILES_EMPTY = "Field 'files' must not be empty";
    private static final String TOO_MANY_FILES = "Upload contains %d files, the maximum is %d";
    private static final String FILE_REQUIRED = "Field 'files[%d]' is required";
    private static final String FILE_SIZE_INVALID = "Field 'files[%d].size' must be >= 0";
    private static final String TOTAL_TOO_LARGE = "Total upload size exceeds the maximum of %d bytes";
    private static final String CHUNKS_EMPTY = "Field 'chunks' must not be empty";
    private static final String TOO_MANY_CHUNKS = "Batch contains %d chunks, the maximum is %d";
    private static final String CHUNK_REQUIRED = "%schunk is required";
    private static final String FILE_NOT_FOUND = "%sFile index %d not found in upload %s";
    private static final String CHUNK_NOT_FOUND = "%sChunk index %d not found: file %d has %d chunk(s)";
    private static final String CHUNK_REPEATED = "%sChunk %d of file %d is repeated in the batch";
    private static final String CHUNK_SIZE_MISMATCH = "%sChunk %d of file %d must be %d bytes before encryption, got %d";
    private static final String PROOF_REQUIRED = "Upload proof is required";
    private static final String PROOF_NOT_EMPTY = "Upload proof must encrypt an empty message";
    private static final String BEING_COMPLETED = "Upload %s is being completed";
    private static final String NOT_ACCEPTING = "Upload %s is not accepting chunks";
    private static final String CHUNKS_MISSING = "%d chunk(s) are missing; first missing: file %d chunk %d";
    private static final String SESSION_NOT_FOUND = "Upload session not found: %s";
    private static final String REVERT_FAILED = "Failed to revert upload {} to ACTIVE";

    private final UploadSessionStore store;
    private final UploadChunkStore chunkStore;
    private final UploadCrypto crypto;
    private final FileStorageService fileStorageService;
    private final JsonMapper jsonMapper;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final FilesProperties.Uploads limits;
    private final StripedLocks locks = new StripedLocks(LOCK_STRIPES);

    @SuppressWarnings("checkstyle:ParameterNumber")
    public UploadSessionServiceImpl(UploadSessionStore store,
                                    UploadChunkStore chunkStore,
                                    UploadCrypto crypto,
                                    FileStorageService fileStorageService,
                                    JsonMapper jsonMapper,
                                    PlatformTransactionManager transactionManager,
                                    Clock clock,
                                    FilesProperties properties) {
        this.store = store;
        this.chunkStore = chunkStore;
        this.crypto = crypto;
        this.fileStorageService = fileStorageService;
        this.jsonMapper = jsonMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.limits = properties.getUploads();
    }

    @Override
    public UploadSessionState create(byte[] keySalt, EncryptedPayload encryptedManifest) {
        byte[] plaintext = crypto.decrypt(keySalt, encryptedManifest.iv(), encryptedManifest.data(),
                UploadCrypto.createAad());
        UploadManifest manifest = readManifest(plaintext);
        String name = FileNameRules.normalizeName(manifest.name());
        List<UploadSessionFile> files = toFiles(manifest);
        Instant now = clock.instant();
        UploadSession session = new UploadSession(UUID.randomUUID(), name, chunkSize(), UploadSessionStatus.ACTIVE,
                now, now.plus(ttl()), keySalt.clone());
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
            throw new InvalidFileException(CHUNKS_EMPTY);
        }
        if (chunks.size() > limits.getMaxBatchChunks()) {
            throw new LimitExceededException(TOO_MANY_CHUNKS.formatted(chunks.size(), limits.getMaxBatchChunks()));
        }
        storeChunks(uploadId, chunks, true);
    }

    @Override
    public UploadSessionState getStatus(UUID uploadId) {
        UploadSession session = find(uploadId);
        if (session.isExpired(clock.instant())) {
            throw new UploadExpiredException(uploadId);
        }
        return new UploadSessionState(session, store.findFiles(uploadId),
                IoActions.unchecked(() -> chunkStore.listReceived(uploadId)));
    }

    @Override
    public StoredFile complete(UUID uploadId, EncryptedPayload proof) {
        UploadSessionState state = locks.write(uploadId, () -> startCompletion(uploadId, proof));
        Path archive = null;
        try {
            archive = IoActions.unchecked(() -> chunkStore.archiveTempPath(uploadId));
            Path builtArchive = archive;
            IoActions.unchecked(() -> TarZstArchiveWriter.write(builtArchive, state.files(),
                    (file, chunk) -> chunkStore.chunkPath(uploadId, file.index(), chunk), clock.instant()));
            StoredFile stored = transactionTemplate.execute(status -> {
                StoredFile file = fileStorageService.storeArchive(state.session().name(), builtArchive);
                if (!store.delete(uploadId)) {
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
        locks.write(uploadId, () -> {
            verifyProof(find(uploadId), proof, ACTION_CANCEL);
            if (!store.delete(uploadId)) {
                throw notFound(uploadId);
            }
            return null;
        });
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
        for (UUID id : IoActions.unchecked(chunkStore::listSessionIds)) {
            deleteWithLock(id, () -> !store.exists(id));
        }
        return removed;
    }

    private UploadManifest readManifest(byte[] plaintext) {
        UploadManifest manifest;
        try {
            manifest = jsonMapper.readValue(plaintext, UploadManifest.class);
        } catch (JacksonException e) {
            throw new InvalidFileException(MANIFEST_NOT_JSON);
        }
        if (manifest == null) {
            throw new InvalidFileException(MANIFEST_EMPTY);
        }
        return manifest;
    }

    private List<UploadSessionFile> toFiles(UploadManifest manifest) {
        List<UploadFileSpec> specs = manifest.files();
        if (specs == null || specs.isEmpty()) {
            throw new InvalidFileException(FILES_EMPTY);
        }
        if (specs.size() > limits.getMaxFiles()) {
            throw new LimitExceededException(TOO_MANY_FILES.formatted(specs.size(), limits.getMaxFiles()));
        }
        List<UploadSessionFile> files = new ArrayList<>(specs.size());
        List<String> paths = new ArrayList<>(specs.size());
        long maxTotalSize = limits.getMaxTotalSize().toBytes();
        long chunkSize = chunkSize();
        long totalSize = 0;
        for (int i = 0; i < specs.size(); i++) {
            UploadFileSpec spec = specs.get(i);
            if (spec == null) {
                throw new InvalidFileException(FILE_REQUIRED.formatted(i));
            }
            String path = UploadPathRules.normalize(spec.path(), i);
            Long size = spec.size();
            if (size == null || size < 0) {
                throw new InvalidFileException(FILE_SIZE_INVALID.formatted(i));
            }
            if (size > maxTotalSize - totalSize) {
                throw new LimitExceededException(TOTAL_TOO_LARGE.formatted(maxTotalSize));
            }
            totalSize += size;
            files.add(new UploadSessionFile(i, path, size, Math.toIntExact((size + chunkSize - 1) / chunkSize)));
            paths.add(path);
        }
        UploadPathRules.checkConflicts(paths);
        return files;
    }

    private void storeChunks(UUID uploadId, List<EncryptedChunk> chunks, boolean batch) {
        UploadSession session = find(uploadId);
        checkActive(session, clock.instant());
        List<DecryptedChunk> decrypted = decrypt(session, chunks, fileLookup(uploadId, batch), batch);
        List<Path> temps = new ArrayList<>(decrypted.size());
        try {
            for (DecryptedChunk chunk : decrypted) {
                temps.add(IoActions.unchecked(() -> chunkStore.writeTemp(uploadId, chunk.fileIndex(),
                        chunk.chunkIndex(), chunk.content())));
            }
            locks.read(uploadId, () -> {
                Instant now = clock.instant();
                if (!store.touch(uploadId, now, now.plus(ttl()))) {
                    throw unavailable(uploadId, now);
                }
                for (int i = 0; i < decrypted.size(); i++) {
                    DecryptedChunk chunk = decrypted.get(i);
                    Path temp = temps.get(i);
                    IoActions.unchecked(() -> chunkStore.commit(temp, uploadId, chunk.fileIndex(), chunk.chunkIndex()));
                }
                return null;
            });
        } finally {
            temps.forEach(chunkStore::deleteQuietly);
        }
    }

    private List<DecryptedChunk> decrypt(UploadSession session, List<EncryptedChunk> chunks,
                                         IntFunction<Optional<UploadSessionFile>> files, boolean batch) {
        List<DecryptedChunk> decrypted = new ArrayList<>(chunks.size());
        Set<Long> seen = new HashSet<>();
        for (int k = 0; k < chunks.size(); k++) {
            String prefix = batch ? BATCH_PREFIX.formatted(k) : StringUtils.EMPTY;
            EncryptedChunk chunk = chunks.get(k);
            if (chunk == null) {
                throw new InvalidFileException(CHUNK_REQUIRED.formatted(prefix));
            }
            int fileIndex = chunk.fileIndex();
            int chunkIndex = chunk.chunkIndex();
            UploadSessionFile file = files.apply(fileIndex).orElseThrow(() ->
                    new UploadNotFoundException(FILE_NOT_FOUND.formatted(prefix, fileIndex, session.id())));
            if (chunkIndex < 0 || chunkIndex >= file.chunkCount()) {
                throw new UploadNotFoundException(CHUNK_NOT_FOUND.formatted(prefix, chunkIndex, fileIndex,
                        file.chunkCount()));
            }
            if (!seen.add(((long) fileIndex << FILE_INDEX_SHIFT) | chunkIndex)) {
                throw new InvalidFileException(CHUNK_REPEATED.formatted(prefix, chunkIndex, fileIndex));
            }
            long expected = expectedChunkSize(session, file, chunkIndex);
            int actual = chunk.payload().data().length - UploadCrypto.TAG_LENGTH;
            if (actual != expected) {
                throw new InvalidFileException(CHUNK_SIZE_MISMATCH.formatted(prefix, chunkIndex, fileIndex, expected,
                        Math.max(actual, 0)));
            }
            byte[] content = crypto.decrypt(session.keySalt(), chunk.payload().iv(), chunk.payload().data(),
                    UploadCrypto.chunkAad(session.id(), fileIndex, chunkIndex));
            decrypted.add(new DecryptedChunk(fileIndex, chunkIndex, content));
        }
        return decrypted;
    }

    private IntFunction<Optional<UploadSessionFile>> fileLookup(UUID uploadId, boolean batch) {
        if (!batch) {
            return index -> store.findFile(uploadId, index);
        }
        Map<Integer, UploadSessionFile> files = new HashMap<>();
        store.findFiles(uploadId).forEach(file -> files.put(file.index(), file));
        return index -> Optional.ofNullable(files.get(index));
    }

    private UploadSessionState startCompletion(UUID uploadId, EncryptedPayload proof) {
        Instant now = clock.instant();
        UploadSession session = find(uploadId);
        verifyProof(session, proof, ACTION_COMPLETE);
        checkActive(session, now);
        List<UploadSessionFile> files = store.findFiles(uploadId);
        checkAllChunksReceived(files, IoActions.unchecked(() -> chunkStore.listReceived(uploadId)));
        store.updateStatus(uploadId, UploadSessionStatus.COMPLETING, now.plus(ttl()));
        return new UploadSessionState(session, files, Map.of());
    }

    private boolean deleteWithLock(UUID id, BooleanSupplier deleteCondition) {
        boolean deleted = locks.write(id, deleteCondition::getAsBoolean);
        if (deleted) {
            chunkStore.deleteSession(id);
        }
        return deleted;
    }

    private void verifyProof(UploadSession session, EncryptedPayload proof, String action) {
        if (proof == null) {
            throw new InvalidFileException(PROOF_REQUIRED);
        }
        byte[] plaintext = crypto.decrypt(session.keySalt(), proof.iv(), proof.data(),
                UploadCrypto.actionAad(action, session.id()));
        if (plaintext.length != 0) {
            throw new InvalidFileException(PROOF_NOT_EMPTY);
        }
    }

    private static void checkActive(UploadSession session, Instant now) {
        if (session.isExpired(now)) {
            throw new UploadExpiredException(session.id());
        }
        if (session.status() == UploadSessionStatus.COMPLETING) {
            throw new UploadConflictException(BEING_COMPLETED.formatted(session.id()));
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
        return new UploadConflictException(NOT_ACCEPTING.formatted(uploadId));
    }

    private static void checkAllChunksReceived(List<UploadSessionFile> files,
                                               Map<Integer, SortedSet<Integer>> received) {
        long missing = 0;
        int firstMissingFile = -1;
        int firstMissingChunk = -1;
        for (UploadSessionFile file : files) {
            Set<Integer> got = received.getOrDefault(file.index(), Collections.emptySortedSet());
            for (int chunk = 0; chunk < file.chunkCount(); chunk++) {
                if (got.contains(chunk)) {
                    continue;
                }
                missing++;
                if (firstMissingFile < 0) {
                    firstMissingFile = file.index();
                    firstMissingChunk = chunk;
                }
            }
        }
        if (missing > 0) {
            throw new UploadConflictException(CHUNKS_MISSING.formatted(missing, firstMissingFile, firstMissingChunk));
        }
    }

    private void revertToActive(UUID uploadId) {
        try {
            locks.write(uploadId, () -> {
                store.find(uploadId)
                        .filter(s -> s.status() == UploadSessionStatus.COMPLETING)
                        .ifPresent(s -> store.updateStatus(uploadId, UploadSessionStatus.ACTIVE,
                                clock.instant().plus(ttl())));
                return null;
            });
        } catch (RuntimeException e) {
            log.warn(REVERT_FAILED, uploadId, e);
        }
    }

    private UploadSession find(UUID uploadId) {
        return store.find(uploadId).orElseThrow(() -> notFound(uploadId));
    }

    private long chunkSize() {
        return limits.getChunkSize().toBytes();
    }

    private Duration ttl() {
        return limits.getTtl();
    }

    private static long expectedChunkSize(UploadSession session, UploadSessionFile file, int chunkIndex) {
        if (chunkIndex < file.chunkCount() - 1) {
            return session.chunkSize();
        }
        return file.size() - session.chunkSize() * (file.chunkCount() - 1);
    }

    private static UploadNotFoundException notFound(UUID uploadId) {
        return new UploadNotFoundException(SESSION_NOT_FOUND.formatted(uploadId));
    }

    private record DecryptedChunk(int fileIndex, int chunkIndex, byte[] content) {
    }
}
