package io.github.admiralxy.agent.service.file;

import io.airlift.compress.v3.zstd.ZstdJavaInputStream;
import io.github.admiralxy.agent.config.properties.AdminProperties;
import io.github.admiralxy.agent.config.properties.FilesProperties;
import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.UploadSessionFile;
import io.github.admiralxy.agent.domain.UploadSessionState;
import io.github.admiralxy.agent.domain.UploadSessionStatus;
import io.github.admiralxy.agent.entity.StoredFileEntity;
import io.github.admiralxy.agent.exception.InvalidFileException;
import io.github.admiralxy.agent.exception.LimitExceededException;
import io.github.admiralxy.agent.exception.UploadConflictException;
import io.github.admiralxy.agent.exception.UploadDecryptionException;
import io.github.admiralxy.agent.exception.UploadExpiredException;
import io.github.admiralxy.agent.exception.UploadNotFoundException;
import io.github.admiralxy.agent.repository.StoredFileRepository;
import io.github.admiralxy.agent.service.file.FileContentStore;
import io.github.admiralxy.agent.service.file.FileStorageServiceImpl;
import io.github.admiralxy.agent.service.file.UploadChunkStore;
import io.github.admiralxy.agent.service.file.UploadCrypto;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadSessionServiceImplTest {

    private static final int CHUNK_SIZE = 4;
    private static final Duration TTL = Duration.ofHours(1);
    private static final String PASSWORD = "test-upload-password";

    @TempDir
    Path tempDir;

    private final SecureRandom random = new SecureRandom();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();
    private final InMemoryUploadSessionStore store = new InMemoryUploadSessionStore();
    private final StoredFileRepository fileRepository = Mockito.mock(StoredFileRepository.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-19T10:00:00Z"));
    private final Map<UUID, byte[]> keySalts = new ConcurrentHashMap<>();
    private FileContentStore contentStore;
    private Path uploadsDir;
    private UploadCrypto crypto;
    private UploadSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        FilesProperties properties = properties();
        uploadsDir = tempDir.resolve("uploads");
        contentStore = new FileContentStore(properties);
        crypto = new UploadCrypto(properties, admin(PASSWORD));
        FileStorageServiceImpl fileStorageService = new FileStorageServiceImpl(fileRepository, contentStore, properties);
        when(fileRepository.save(any())).thenAnswer(inv -> {
            StoredFileEntity entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        service = new UploadSessionServiceImpl(store, new UploadChunkStore(properties), crypto, fileStorageService,
                jsonMapper, new NoOpTransactionManager(), clock, properties);
    }

    private static AdminProperties admin(String password) {
        AdminProperties admin = new AdminProperties();
        admin.setPassword(password);
        admin.afterPropertiesSet();
        return admin;
    }

    private FilesProperties properties() {
        FilesProperties properties = new FilesProperties();
        properties.setStorageDir(tempDir.resolve("files").toString());
        FilesProperties.Uploads uploads = properties.getUploads();
        uploads.setChunkSize(DataSize.ofBytes(CHUNK_SIZE));
        uploads.setMaxTotalSize(DataSize.ofBytes(1000));
        uploads.setMaxFiles(100);
        uploads.setMaxBatchChunks(50);
        uploads.setTtl(TTL);
        uploads.setTempDir(tempDir.resolve("uploads").toString());
        uploads.getEncryption().setKdfIterations(1000);
        properties.afterPropertiesSet();
        return properties;
    }

    @Test
    void completeBuildsTarZstWithNestedFoldersCyrillicPathsAndEmptyFiles() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("Мой проект/src/main.ts", bytes("console.log('привет')"));
        files.put("Мой проект/README.md", new byte[0]);
        files.put("Мой проект/docs/глава 1/текст.txt", bytes("abcd"));
        files.put("Мой проект/empty/.keep", new byte[0]);
        UploadSessionState state = create("Мой проект", files);

        assertEquals(CHUNK_SIZE, state.session().chunkSize());
        assertEquals(List.of(7, 0, 1, 0), state.files().stream().map(UploadSessionFile::chunkCount).toList());

        sendAllOneByOne(state, files, true);
        StoredFile stored = complete(state.session().id());

        assertEquals("Мой проект.tar.zst", stored.fileName());
        assertEquals("tar.zst", stored.extension());
        assertEquals("application/zstd", stored.contentType());
        Path archive = storedArchive();
        assertEquals(Files.size(archive), stored.size());

        Map<String, byte[]> entries = untar(archive);
        assertEquals(files.keySet(), fileEntries(entries).keySet());
        files.forEach((path, content) -> assertArrayEquals(content, entries.get(path), path));
        assertTrue(entries.containsKey("Мой проект/"));
        assertTrue(entries.containsKey("Мой проект/docs/глава 1/"));
        assertTrue(hasZstdMagic(archive));
        assertTrue(hasNeutralOwnership(archive));

        assertThrows(UploadNotFoundException.class, () -> service.getStatus(state.session().id()));
        assertFalse(Files.exists(uploadsDir.resolve(state.session().id().toString())));
    }

    @Test
    void smallFilesAreUploadedInOneBatch() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            files.put("src/file-" + i + ".txt", bytes("f" + i));
        }
        files.put("big.bin", bytes("0123456789"));
        UploadSessionState state = create("mixed", files);
        UUID id = state.session().id();
        List<byte[]> contents = List.copyOf(files.values());

        List<EncryptedChunk> batch = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            batch.add(chunk(id, i, 0, contents.get(i)));
        }
        service.putChunks(id, batch);
        for (int c = 0; c < 3; c++) {
            put(id, 30, c, slice(contents.get(30), c));
        }
        complete(id);

        Map<String, byte[]> entries = untar(storedArchive());
        files.forEach((path, content) -> assertArrayEquals(content, entries.get(path), path));
    }

    @Test
    void invalidBatchIsRejectedEntirely() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("a.txt", bytes("ab"));
        files.put("b.txt", bytes("cd"));
        UUID id = create("a", files).session().id();

        InvalidFileException e = assertThrows(InvalidFileException.class, () -> service.putChunks(id, List.of(
                chunk(id, 0, 0, bytes("ab")),
                chunk(id, 1, 0, bytes("cde")))));
        assertTrue(e.getMessage().startsWith("chunks[1]: "), e.getMessage());

        assertThrows(UploadDecryptionException.class, () -> service.putChunks(id, List.of(
                chunk(id, 0, 0, bytes("ab")),
                new EncryptedChunk(1, 0, chunk(id, 0, 0, bytes("cd")).payload()))));

        assertTrue(service.getStatus(id).receivedChunks().isEmpty());
    }

    @Test
    void batchLimitsAreEnforced() {
        UUID id = create("a", Map.of("a.txt", bytes("ab"))).session().id();
        EncryptedChunk chunk = chunk(id, 0, 0, bytes("ab"));

        assertThrows(InvalidFileException.class, () -> service.putChunks(id, List.of()));
        assertThrows(InvalidFileException.class, () -> service.putChunks(id, List.of(chunk, chunk)));
        assertThrows(LimitExceededException.class, () -> service.putChunks(id, Collections.nCopies(51, chunk)));
    }

    @Test
    void resendingChunkOverwritesPreviousContent() throws Exception {
        UploadSessionState state = create("a", Map.of("a.txt", bytes("abcdef")));
        UUID id = state.session().id();

        put(id, 0, 0, bytes("XXXX"));
        put(id, 0, 1, bytes("ef"));
        put(id, 0, 0, bytes("abcd"));
        complete(id);

        assertArrayEquals(bytes("abcdef"), untar(storedArchive()).get("a.txt"));
    }

    @Test
    void chunkWithWrongSizeIsRejected() {
        UploadSessionState state = create("a", Map.of("a.txt", bytes("abcdef")));
        UUID id = state.session().id();

        InvalidFileException shortChunk = assertThrows(InvalidFileException.class, () -> put(id, 0, 0, bytes("abc")));
        assertEquals("Chunk 0 of file 0 must be 4 bytes before encryption, got 3", shortChunk.getMessage());
        assertThrows(InvalidFileException.class, () -> put(id, 0, 1, bytes("efg")));
        assertTrue(service.getStatus(id).receivedChunks().isEmpty());
    }

    @Test
    void wrongPasswordIsRejectedOnCreate() {
        UploadCrypto otherCrypto = new UploadCrypto(properties(), admin("another-upload-password"));
        byte[] keySalt = randomBytes(UploadCrypto.KEY_SALT_LENGTH);
        byte[] iv = randomBytes(UploadCrypto.IV_LENGTH);
        byte[] manifest = jsonMapper.writeValueAsBytes(new UploadManifest("a", List.of(new UploadFileSpec("a.txt", 1L))));
        byte[] data = otherCrypto.encrypt(keySalt, iv, manifest, UploadCrypto.createAad());

        assertThrows(UploadDecryptionException.class, () -> service.create(keySalt, new EncryptedPayload(iv, data)));
    }

    @Test
    void chunksAreBoundToTheirPositionAndSession() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("a.txt", bytes("ab"));
        files.put("b.txt", bytes("cd"));
        UUID id = create("a", files).session().id();
        UUID other = create("b", files).session().id();

        EncryptedPayload forFile0 = chunk(id, 0, 0, bytes("cd")).payload();
        assertThrows(UploadDecryptionException.class, () -> service.putChunk(id, new EncryptedChunk(1, 0, forFile0)));
        assertThrows(UploadDecryptionException.class, () -> service.putChunk(other, new EncryptedChunk(0, 0, forFile0)));
    }

    @Test
    void completeAndCancelRequireValidProof() {
        UUID id = create("a", Map.of("a.txt", new byte[0])).session().id();
        UUID other = create("b", Map.of("b.txt", new byte[0])).session().id();

        assertThrows(UploadDecryptionException.class, () -> service.complete(id, proof(id, "cancel")));
        assertThrows(UploadDecryptionException.class, () -> service.complete(id, proof(other, "complete")));
        assertThrows(UploadDecryptionException.class, () -> service.cancel(id, proof(id, "complete")));
        assertTrue(store.exists(id));

        complete(id);
    }

    @Test
    void completeWithMissingChunksReturnsConflictWithoutPaths() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("secret-dir/first.bin", bytes("abcdefgh"));
        files.put("secret-dir/second.bin", bytes("ab"));
        UploadSessionState state = create("a", files);
        UUID id = state.session().id();
        put(id, 0, 1, bytes("efgh"));

        UploadConflictException e = assertThrows(UploadConflictException.class, () -> complete(id));

        assertEquals("2 chunk(s) are missing; first missing: file 0 chunk 0", e.getMessage());
        assertEquals(UploadSessionStatus.ACTIVE, store.find(id).orElseThrow().status());
    }

    @Test
    void statusReportsReceivedChunks() {
        UploadSessionState state = create("a", Map.of("a.bin", new byte[10]));
        UUID id = state.session().id();
        put(id, 0, 2, new byte[2]);
        put(id, 0, 0, new byte[4]);

        UploadSessionState status = service.getStatus(id);

        assertEquals(List.of(0, 2), List.copyOf(status.receivedChunks().get(0)));
    }

    @Test
    void unsafePathsAreRejected() {
        List<String> invalid = List.of("../secret", "a/../b", "/etc/passwd", "C:/x.txt", "C:\\x.txt", "\\\\server\\share",
                "a//b", "a/./b", "a/", ".", "", "a\u0001b", "x".repeat(1025));
        for (String path : invalid) {
            InvalidFileException e = assertThrows(InvalidFileException.class,
                    () -> createRaw("a", List.of(new UploadFileSpec(path, 1L))), path);
            assertTrue(e.getMessage().startsWith("Field 'files[0].path'"), e.getMessage());
        }
    }

    @Test
    void backslashesAreNormalized() {
        UploadSessionState state = createRaw("a", List.of(new UploadFileSpec("dir\\sub\\file.txt", 1L)));

        assertEquals("dir/sub/file.txt", state.files().getFirst().path());
    }

    @Test
    void duplicateAndConflictingPathsAreRejectedByIndex() {
        InvalidFileException duplicate = assertThrows(InvalidFileException.class, () -> createRaw("a", List.of(
                new UploadFileSpec("Dir/File.txt", 1L), new UploadFileSpec("dir/file.TXT", 1L))));
        assertEquals("Field 'files[1].path' duplicates 'files[0].path' (case-insensitive)", duplicate.getMessage());

        InvalidFileException conflict = assertThrows(InvalidFileException.class, () -> createRaw("a", List.of(
                new UploadFileSpec("a", 1L), new UploadFileSpec("A/b.txt", 1L))));
        assertEquals("Field 'files[1].path' uses 'files[0].path' as a folder, but it is a file", conflict.getMessage());
    }

    @Test
    void invalidSessionRequestsAreRejected() {
        assertThrows(InvalidFileException.class, () -> createRaw("a", List.of()));
        assertThrows(InvalidFileException.class, () -> createRaw("a", null));
        assertThrows(InvalidFileException.class, () -> createRaw("a", List.of(new UploadFileSpec("a.txt", -1L))));
        assertThrows(InvalidFileException.class, () -> createRaw("a", List.of(new UploadFileSpec("a.txt", null))));
        assertThrows(InvalidFileException.class, () -> createRaw("../a", List.of(new UploadFileSpec("a.txt", 1L))));
        byte[] keySalt = randomBytes(UploadCrypto.KEY_SALT_LENGTH);
        byte[] iv = randomBytes(UploadCrypto.IV_LENGTH);
        byte[] notJson = crypto.encrypt(keySalt, iv, bytes("not json"), UploadCrypto.createAad());
        assertThrows(InvalidFileException.class, () -> service.create(keySalt, new EncryptedPayload(iv, notJson)));
    }

    @Test
    void limitsAreEnforced() {
        List<UploadFileSpec> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(new UploadFileSpec("f" + i, 1L));
        }
        assertThrows(LimitExceededException.class, () -> createRaw("a", tooMany));
        assertThrows(LimitExceededException.class, () -> createRaw("a", List.of(
                new UploadFileSpec("a", 600L), new UploadFileSpec("b", 401L))));
        createRaw("a", List.of(new UploadFileSpec("a", 600L), new UploadFileSpec("b", 400L)));
    }

    @Test
    void unknownIndexesReturnNotFound() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("a.txt", bytes("ab"));
        files.put("empty.txt", new byte[0]);
        UUID id = create("a", files).session().id();
        EncryptedPayload any = chunk(id, 0, 0, bytes("ab")).payload();

        assertThrows(UploadNotFoundException.class, () -> service.putChunk(UUID.randomUUID(), new EncryptedChunk(0, 0, any)));
        assertThrows(UploadNotFoundException.class, () -> service.putChunk(id, new EncryptedChunk(5, 0, any)));
        assertThrows(UploadNotFoundException.class, () -> service.putChunk(id, new EncryptedChunk(0, 1, any)));
        assertThrows(UploadNotFoundException.class, () -> service.putChunk(id, new EncryptedChunk(0, -1, any)));
        assertThrows(UploadNotFoundException.class, () -> service.putChunk(id, new EncryptedChunk(1, 0, any)));
    }

    @Test
    void expiredSessionIsRejectedAndCleanedUp() {
        UUID id = create("a", Map.of("a.txt", bytes("abcdef"))).session().id();
        put(id, 0, 0, bytes("abcd"));

        clock.advance(TTL.plusSeconds(1));

        assertThrows(UploadExpiredException.class, () -> put(id, 0, 1, bytes("ef")));
        assertThrows(UploadExpiredException.class, () -> service.getStatus(id));
        assertThrows(UploadExpiredException.class, () -> complete(id));
        assertTrue(Files.exists(uploadsDir.resolve(id.toString())));

        assertEquals(1, service.cleanupExpired());

        assertThrows(UploadNotFoundException.class, () -> service.getStatus(id));
        assertFalse(Files.exists(uploadsDir.resolve(id.toString())));
    }

    @Test
    void chunkUploadExtendsExpiration() {
        UUID id = create("a", Map.of("a.txt", bytes("abcdef"))).session().id();

        clock.advance(Duration.ofMinutes(50));
        put(id, 0, 0, bytes("abcd"));
        clock.advance(Duration.ofMinutes(50));

        assertEquals(clock.instant().minus(Duration.ofMinutes(50)).plus(TTL), service.getStatus(id).session().expiresAt());
        put(id, 0, 1, bytes("ef"));
        assertEquals(0, service.cleanupExpired());
        complete(id);
    }

    @Test
    void cleanupRemovesOrphanedDirectories() throws Exception {
        Path orphan = Files.createDirectories(uploadsDir.resolve(UUID.randomUUID().toString()));
        Files.write(orphan.resolve("0.0.part"), bytes("x"));
        UUID active = create("a", Map.of("a.txt", bytes("ab"))).session().id();
        put(active, 0, 0, bytes("ab"));

        service.cleanupExpired();

        assertFalse(Files.exists(orphan));
        assertTrue(Files.exists(uploadsDir.resolve(active.toString())));
    }

    @Test
    void chunksAreRejectedWhileCompleting() {
        UUID id = create("a", Map.of("a.txt", bytes("ab"))).session().id();
        store.updateStatus(id, UploadSessionStatus.COMPLETING, clock.instant().plus(TTL));

        assertThrows(UploadConflictException.class, () -> put(id, 0, 0, bytes("ab")));
        assertThrows(UploadConflictException.class, () -> complete(id));
    }

    @Test
    void secondCompleteAndCancelledSessionReturnNotFound() {
        UUID completed = create("a", Map.of("a.txt", new byte[0])).session().id();
        complete(completed);
        assertThrows(UploadNotFoundException.class, () -> complete(completed));

        UUID cancelled = create("b", Map.of("b.txt", bytes("ab"))).session().id();
        put(cancelled, 0, 0, bytes("ab"));
        cancel(cancelled);

        assertFalse(Files.exists(uploadsDir.resolve(cancelled.toString())));
        assertThrows(UploadNotFoundException.class, () -> put(cancelled, 0, 0, bytes("ab")));
        assertThrows(UploadNotFoundException.class, () -> complete(cancelled));
        assertThrows(UploadNotFoundException.class, () -> cancel(cancelled));
    }

    @Test
    void parallelSingleAndBatchUploadsAreAllRecorded() throws Exception {
        Random seeded = new Random(42);
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            byte[] content = new byte[150 + i];
            seeded.nextBytes(content);
            files.put("parallel/file-" + i + ".bin", content);
        }
        UploadSessionState state = create("parallel", files);
        UUID id = state.session().id();
        List<byte[]> contents = List.copyOf(files.values());

        List<Runnable> tasks = new ArrayList<>();
        for (UploadSessionFile file : state.files()) {
            List<EncryptedChunk> fileChunks = new ArrayList<>();
            for (int chunk = 0; chunk < file.chunkCount(); chunk++) {
                EncryptedChunk encrypted = chunk(id, file.index(), chunk, slice(contents.get(file.index()), chunk));
                fileChunks.add(encrypted);
                tasks.add(() -> service.putChunk(id, encrypted));
            }
            for (int from = 0; from < fileChunks.size(); from += 10) {
                List<EncryptedChunk> batch = fileChunks.subList(from, Math.min(fileChunks.size(), from + 10));
                tasks.add(() -> service.putChunks(id, batch));
            }
        }
        Collections.shuffle(tasks, seeded);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = tasks.stream().<Future<?>>map(executor::submit).toList();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        complete(id);

        Map<String, byte[]> entries = untar(storedArchive());
        files.forEach((path, content) -> assertArrayEquals(content, entries.get(path), path));
    }

    private UploadSessionState create(String name, Map<String, byte[]> files) {
        return createRaw(name, files.entrySet().stream()
                .map(e -> new UploadFileSpec(e.getKey(), (long) e.getValue().length))
                .toList());
    }

    private UploadSessionState createRaw(String name, List<UploadFileSpec> files) {
        byte[] keySalt = randomBytes(UploadCrypto.KEY_SALT_LENGTH);
        byte[] iv = randomBytes(UploadCrypto.IV_LENGTH);
        byte[] manifest = jsonMapper.writeValueAsBytes(new UploadManifest(name, files));
        byte[] data = crypto.encrypt(keySalt, iv, manifest, UploadCrypto.createAad());
        UploadSessionState state = service.create(keySalt, new EncryptedPayload(iv, data));
        keySalts.put(state.session().id(), keySalt);
        return state;
    }

    private EncryptedChunk chunk(UUID id, int fileIndex, int chunkIndex, byte[] content) {
        byte[] iv = randomBytes(UploadCrypto.IV_LENGTH);
        byte[] data = crypto.encrypt(keySalts.get(id), iv, content, UploadCrypto.chunkAad(id, fileIndex, chunkIndex));
        return new EncryptedChunk(fileIndex, chunkIndex, new EncryptedPayload(iv, data));
    }

    private void put(UUID id, int fileIndex, int chunkIndex, byte[] content) {
        service.putChunk(id, chunk(id, fileIndex, chunkIndex, content));
    }

    private EncryptedPayload proof(UUID id, String action) {
        byte[] iv = randomBytes(UploadCrypto.IV_LENGTH);
        return new EncryptedPayload(iv, crypto.encrypt(keySalts.get(id), iv, new byte[0], UploadCrypto.actionAad(action, id)));
    }

    private StoredFile complete(UUID id) {
        return service.complete(id, proof(id, "complete"));
    }

    private void cancel(UUID id) {
        service.cancel(id, proof(id, "cancel"));
    }

    private void sendAllOneByOne(UploadSessionState state, Map<String, byte[]> files, boolean reverse) {
        List<byte[]> contents = List.copyOf(files.values());
        List<UploadSessionFile> sessionFiles = new ArrayList<>(state.files());
        if (reverse) {
            Collections.reverse(sessionFiles);
        }
        for (UploadSessionFile file : sessionFiles) {
            for (int i = 0; i < file.chunkCount(); i++) {
                int chunk = reverse ? file.chunkCount() - 1 - i : i;
                put(state.session().id(), file.index(), chunk, slice(contents.get(file.index()), chunk));
            }
        }
    }

    private Path storedArchive() {
        ArgumentCaptor<StoredFileEntity> captor = ArgumentCaptor.forClass(StoredFileEntity.class);
        verify(fileRepository, Mockito.atLeastOnce()).save(captor.capture());
        return contentStore.resolve(captor.getValue().getStoragePath());
    }

    private static Map<String, byte[]> untar(Path archive) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(archive);
             InputStream zstd = new ZstdJavaInputStream(in);
             TarArchiveInputStream tar = new TarArchiveInputStream(zstd, StandardCharsets.UTF_8.name())) {
            for (TarArchiveEntry entry = tar.getNextEntry(); entry != null; entry = tar.getNextEntry()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                tar.transferTo(out);
                entries.put(entry.getName(), out.toByteArray());
            }
        }
        return entries;
    }

    private static boolean hasNeutralOwnership(Path archive) throws Exception {
        try (InputStream in = Files.newInputStream(archive);
             InputStream zstd = new ZstdJavaInputStream(in);
             TarArchiveInputStream tar = new TarArchiveInputStream(zstd, StandardCharsets.UTF_8.name())) {
            for (TarArchiveEntry entry = tar.getNextEntry(); entry != null; entry = tar.getNextEntry()) {
                if (!entry.getUserName().isEmpty() || !entry.getGroupName().isEmpty()
                        || entry.getLongUserId() != 0 || entry.getLongGroupId() != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    private static Map<String, byte[]> fileEntries(Map<String, byte[]> entries) {
        Map<String, byte[]> result = new LinkedHashMap<>(entries);
        result.keySet().removeIf(name -> name.endsWith("/"));
        return result;
    }

    private static boolean hasZstdMagic(Path archive) throws Exception {
        byte[] header = Arrays.copyOf(Files.readAllBytes(archive), 4);
        return Arrays.equals(header, new byte[]{(byte) 0x28, (byte) 0xB5, (byte) 0x2F, (byte) 0xFD});
    }

    private static byte[] slice(byte[] content, int chunk) {
        int from = chunk * CHUNK_SIZE;
        return Arrays.copyOfRange(content, from, Math.min(content.length, from + CHUNK_SIZE));
    }

    private byte[] randomBytes(int length) {
        byte[] value = new byte[length];
        random.nextBytes(value);
        return value;
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
