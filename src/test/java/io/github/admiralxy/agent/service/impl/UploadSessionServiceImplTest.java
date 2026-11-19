package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.config.properties.FilesProperties;
import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.UploadSessionFile;
import io.github.admiralxy.agent.domain.UploadSessionState;
import io.github.admiralxy.agent.domain.UploadSessionStatus;
import io.github.admiralxy.agent.entity.StoredFileEntity;
import io.github.admiralxy.agent.exception.InvalidFileException;
import io.github.admiralxy.agent.exception.LimitExceededException;
import io.github.admiralxy.agent.exception.UploadConflictException;
import io.github.admiralxy.agent.exception.UploadExpiredException;
import io.github.admiralxy.agent.exception.UploadNotFoundException;
import io.github.admiralxy.agent.repository.StoredFileRepository;
import io.github.admiralxy.agent.service.UploadFileSpec;
import io.github.admiralxy.agent.service.file.FileContentStore;
import io.github.admiralxy.agent.service.file.UploadChunkStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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

    @TempDir
    Path tempDir;

    private final InMemoryUploadSessionStore store = new InMemoryUploadSessionStore();
    private final StoredFileRepository fileRepository = Mockito.mock(StoredFileRepository.class);
    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-19T10:00:00Z"));
    private FileContentStore contentStore;
    private Path uploadsDir;
    private UploadSessionServiceImpl service;

    @BeforeEach
    void setUp() {
        FilesProperties properties = new FilesProperties();
        properties.setStorageDir(tempDir.resolve("files").toString());
        FilesProperties.Uploads uploads = properties.getUploads();
        uploads.setChunkSize(DataSize.ofBytes(CHUNK_SIZE));
        uploads.setMaxTotalSize(DataSize.ofBytes(1000));
        uploads.setMaxFiles(10);
        uploads.setTtl(TTL);
        uploadsDir = tempDir.resolve("uploads");
        uploads.setTempDir(uploadsDir.toString());
        properties.afterPropertiesSet();

        contentStore = new FileContentStore(properties);
        FileStorageServiceImpl fileStorageService = new FileStorageServiceImpl(fileRepository, contentStore, properties);
        when(fileRepository.save(any())).thenAnswer(inv -> {
            StoredFileEntity entity = inv.getArgument(0);
            entity.setId(UUID.randomUUID());
            return entity;
        });
        service = new UploadSessionServiceImpl(
                store, new UploadChunkStore(properties), fileStorageService, new NoOpTransactionManager(), clock, properties);
    }

    @Test
    void completeBuildsZipWithNestedFoldersCyrillicPathsAndEmptyFiles() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("Мой проект/src/main.ts", bytes("console.log('привет')"));
        files.put("Мой проект/README.md", new byte[0]);
        files.put("Мой проект/docs/глава 1/текст.txt", bytes("abcd"));
        files.put("Мой проект/empty/.keep", new byte[0]);
        UploadSessionState state = create("Мой проект", files);

        assertEquals(CHUNK_SIZE, state.session().chunkSize());
        assertEquals(List.of(7, 0, 1, 0), state.files().stream().map(UploadSessionFile::chunkCount).toList());

        sendAll(state, files, true);
        StoredFile stored = service.complete(state.session().id());

        assertEquals("Мой проект.zip", stored.fileName());
        assertEquals("application/zip", stored.contentType());
        Path archive = storedArchive();
        assertEquals(Files.size(archive), stored.size());

        Map<String, byte[]> entries = unzip(archive);
        assertEquals(files.keySet(), fileEntries(entries).keySet());
        files.forEach((path, content) -> assertArrayEquals(content, entries.get(path), path));
        assertTrue(entries.containsKey("Мой проект/"));
        assertTrue(entries.containsKey("Мой проект/docs/глава 1/"));
        assertTrue(hasUtf8Flag(archive));

        assertThrows(UploadNotFoundException.class, () -> service.getStatus(state.session().id()));
        assertFalse(Files.exists(uploadsDir.resolve(state.session().id().toString())));
    }

    @Test
    void chunksAcceptDataUrls() throws Exception {
        UploadSessionState state = create("a", Map.of("a.txt", bytes("hello")));
        UUID id = state.session().id();

        service.putChunk(id, 0, 0, "data:application/octet-stream;base64," + b64(bytes("hell")));
        service.putChunk(id, 0, 1, b64(bytes("o")));
        service.complete(id);

        assertArrayEquals(bytes("hello"), unzip(storedArchive()).get("a.txt"));
    }

    @Test
    void resendingChunkOverwritesPreviousContent() throws Exception {
        UploadSessionState state = create("a", Map.of("a.txt", bytes("abcdef")));
        UUID id = state.session().id();

        service.putChunk(id, 0, 0, b64(bytes("XXXX")));
        service.putChunk(id, 0, 1, b64(bytes("ef")));
        service.putChunk(id, 0, 0, b64(bytes("abcd")));
        service.complete(id);

        assertArrayEquals(bytes("abcdef"), unzip(storedArchive()).get("a.txt"));
    }

    @Test
    void chunkWithWrongSizeIsRejected() {
        UploadSessionState state = create("a", Map.of("a.txt", bytes("abcdef")));
        UUID id = state.session().id();

        InvalidFileException shortChunk = assertThrows(InvalidFileException.class, () -> service.putChunk(id, 0, 0, b64(bytes("abc"))));
        assertTrue(shortChunk.getMessage().contains("must be 4 bytes, got 3"), shortChunk.getMessage());
        assertThrows(InvalidFileException.class, () -> service.putChunk(id, 0, 1, b64(bytes("efg"))));
        assertThrows(InvalidFileException.class, () -> service.putChunk(id, 0, 0, "%%%"));
        assertTrue(service.getStatus(id).receivedChunks().isEmpty());
    }

    @Test
    void completeWithMissingChunksReturnsConflict() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("dir/first.bin", bytes("abcdefgh"));
        files.put("dir/second.bin", bytes("ab"));
        UploadSessionState state = create("a", files);
        UUID id = state.session().id();
        service.putChunk(id, 0, 1, b64(bytes("efgh")));

        UploadConflictException e = assertThrows(UploadConflictException.class, () -> service.complete(id));

        assertTrue(e.getMessage().startsWith("2 chunk(s) are missing"), e.getMessage());
        assertTrue(e.getMessage().contains("'dir/first.bin' chunk 0"), e.getMessage());
        assertEquals(UploadSessionStatus.ACTIVE, store.find(id).orElseThrow().status());
    }

    @Test
    void statusReportsReceivedChunks() {
        UploadSessionState state = create("a", Map.of("a.bin", new byte[10]));
        UUID id = state.session().id();
        service.putChunk(id, 0, 2, b64(new byte[2]));
        service.putChunk(id, 0, 0, b64(new byte[4]));

        UploadSessionState status = service.getStatus(id);

        assertEquals(List.of(0, 2), List.copyOf(status.receivedChunks().get(0)));
    }

    @Test
    void unsafePathsAreRejected() {
        List<String> invalid = List.of("../secret", "a/../b", "/etc/passwd", "C:/x.txt", "C:\\x.txt", "\\\\server\\share",
                "a//b", "a/./b", "a/", ".", "", "a\u0001b", "x".repeat(1025));
        for (String path : invalid) {
            assertThrows(InvalidFileException.class, () -> createRaw("a", List.of(new UploadFileSpec(path, 1L))), path);
        }
    }

    @Test
    void backslashesAreNormalized() {
        UploadSessionState state = createRaw("a", List.of(new UploadFileSpec("dir\\sub\\file.txt", 1L)));

        assertEquals("dir/sub/file.txt", state.files().getFirst().path());
    }

    @Test
    void duplicateAndConflictingPathsAreRejected() {
        assertThrows(InvalidFileException.class, () -> createRaw("a", List.of(
                new UploadFileSpec("Dir/File.txt", 1L), new UploadFileSpec("dir/file.TXT", 1L))));
        assertThrows(InvalidFileException.class, () -> createRaw("a", List.of(
                new UploadFileSpec("a", 1L), new UploadFileSpec("A/b.txt", 1L))));
    }

    @Test
    void invalidSessionRequestsAreRejected() {
        assertThrows(InvalidFileException.class, () -> createRaw("a", List.of()));
        assertThrows(InvalidFileException.class, () -> createRaw("a", null));
        assertThrows(InvalidFileException.class, () -> createRaw("a", List.of(new UploadFileSpec("a.txt", -1L))));
        assertThrows(InvalidFileException.class, () -> createRaw("a", List.of(new UploadFileSpec("a.txt", null))));
        assertThrows(InvalidFileException.class, () -> createRaw("../a", List.of(new UploadFileSpec("a.txt", 1L))));
    }

    @Test
    void limitsAreEnforced() {
        List<UploadFileSpec> tooMany = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
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

        assertThrows(UploadNotFoundException.class, () -> service.putChunk(UUID.randomUUID(), 0, 0, b64(bytes("ab"))));
        assertThrows(UploadNotFoundException.class, () -> service.putChunk(id, 5, 0, b64(bytes("ab"))));
        assertThrows(UploadNotFoundException.class, () -> service.putChunk(id, 0, 1, b64(bytes("ab"))));
        assertThrows(UploadNotFoundException.class, () -> service.putChunk(id, 0, -1, b64(bytes("ab"))));
        assertThrows(UploadNotFoundException.class, () -> service.putChunk(id, 1, 0, b64(bytes("ab"))));
    }

    @Test
    void expiredSessionIsRejectedAndCleanedUp() {
        UUID id = create("a", Map.of("a.txt", bytes("abcdef"))).session().id();
        service.putChunk(id, 0, 0, b64(bytes("abcd")));

        clock.advance(TTL.plusSeconds(1));

        assertThrows(UploadExpiredException.class, () -> service.putChunk(id, 0, 1, b64(bytes("ef"))));
        assertThrows(UploadExpiredException.class, () -> service.getStatus(id));
        assertThrows(UploadExpiredException.class, () -> service.complete(id));
        assertTrue(Files.exists(uploadsDir.resolve(id.toString())));

        assertEquals(1, service.cleanupExpired());

        assertThrows(UploadNotFoundException.class, () -> service.getStatus(id));
        assertFalse(Files.exists(uploadsDir.resolve(id.toString())));
    }

    @Test
    void chunkUploadExtendsExpiration() {
        UUID id = create("a", Map.of("a.txt", bytes("abcdef"))).session().id();

        clock.advance(Duration.ofMinutes(50));
        service.putChunk(id, 0, 0, b64(bytes("abcd")));
        clock.advance(Duration.ofMinutes(50));

        assertEquals(clock.instant().minus(Duration.ofMinutes(50)).plus(TTL), service.getStatus(id).session().expiresAt());
        service.putChunk(id, 0, 1, b64(bytes("ef")));
        assertEquals(0, service.cleanupExpired());
        service.complete(id);
    }

    @Test
    void cleanupRemovesOrphanedDirectories() throws Exception {
        Path orphan = Files.createDirectories(uploadsDir.resolve(UUID.randomUUID().toString()));
        Files.write(orphan.resolve("0.0.part"), bytes("x"));
        UUID active = create("a", Map.of("a.txt", bytes("ab"))).session().id();
        service.putChunk(active, 0, 0, b64(bytes("ab")));

        service.cleanupExpired();

        assertFalse(Files.exists(orphan));
        assertTrue(Files.exists(uploadsDir.resolve(active.toString())));
    }

    @Test
    void chunksAreRejectedWhileCompleting() {
        UUID id = create("a", Map.of("a.txt", bytes("ab"))).session().id();
        store.updateStatus(id, UploadSessionStatus.COMPLETING, clock.instant().plus(TTL));

        assertThrows(UploadConflictException.class, () -> service.putChunk(id, 0, 0, b64(bytes("ab"))));
        assertThrows(UploadConflictException.class, () -> service.complete(id));
    }

    @Test
    void secondCompleteAndCancelledSessionReturnNotFound() {
        UUID completed = create("a", Map.of("a.txt", new byte[0])).session().id();
        service.complete(completed);
        assertThrows(UploadNotFoundException.class, () -> service.complete(completed));

        UUID cancelled = create("b", Map.of("b.txt", bytes("ab"))).session().id();
        service.putChunk(cancelled, 0, 0, b64(bytes("ab")));
        service.cancel(cancelled);

        assertFalse(Files.exists(uploadsDir.resolve(cancelled.toString())));
        assertThrows(UploadNotFoundException.class, () -> service.putChunk(cancelled, 0, 0, b64(bytes("ab"))));
        assertThrows(UploadNotFoundException.class, () -> service.complete(cancelled));
        assertThrows(UploadNotFoundException.class, () -> service.cancel(cancelled));
    }

    @Test
    void parallelChunkUploadsAreAllRecorded() throws Exception {
        Random random = new Random(42);
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            byte[] content = new byte[150 + i];
            random.nextBytes(content);
            files.put("parallel/file-" + i + ".bin", content);
        }
        UploadSessionState state = create("parallel", files);
        UUID id = state.session().id();
        List<byte[]> contents = List.copyOf(files.values());

        List<Runnable> tasks = new ArrayList<>();
        for (UploadSessionFile file : state.files()) {
            for (int chunk = 0; chunk < file.chunkCount(); chunk++) {
                int index = file.index();
                int chunkIndex = chunk;
                String payload = b64(slice(contents.get(index), chunkIndex));
                tasks.add(() -> service.putChunk(id, index, chunkIndex, payload));
                // duplicates race with the original upload of the same chunk
                tasks.add(() -> service.putChunk(id, index, chunkIndex, payload));
            }
        }
        Collections.shuffle(tasks, random);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = tasks.stream().<Future<?>>map(executor::submit).toList();
            for (Future<?> future : futures) {
                future.get();
            }
        } finally {
            executor.shutdownNow();
        }

        service.complete(id);

        Map<String, byte[]> entries = unzip(storedArchive());
        files.forEach((path, content) -> assertArrayEquals(content, entries.get(path), path));
    }

    private UploadSessionState create(String name, Map<String, byte[]> files) {
        return createRaw(name, files.entrySet().stream()
                .map(e -> new UploadFileSpec(e.getKey(), (long) e.getValue().length))
                .toList());
    }

    private UploadSessionState createRaw(String name, List<UploadFileSpec> files) {
        return service.create(name, files);
    }

    private void sendAll(UploadSessionState state, Map<String, byte[]> files, boolean reverse) {
        List<byte[]> contents = List.copyOf(files.values());
        List<UploadSessionFile> sessionFiles = new ArrayList<>(state.files());
        if (reverse) {
            Collections.reverse(sessionFiles);
        }
        for (UploadSessionFile file : sessionFiles) {
            for (int i = 0; i < file.chunkCount(); i++) {
                int chunk = reverse ? file.chunkCount() - 1 - i : i;
                service.putChunk(state.session().id(), file.index(), chunk, b64(slice(contents.get(file.index()), chunk)));
            }
        }
    }

    private Path storedArchive() {
        ArgumentCaptor<StoredFileEntity> captor = ArgumentCaptor.forClass(StoredFileEntity.class);
        verify(fileRepository, Mockito.atLeastOnce()).save(captor.capture());
        return contentStore.resolve(captor.getValue().getStoragePath());
    }

    private static Map<String, byte[]> unzip(Path archive) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(archive);
             ZipInputStream zip = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                zip.transferTo(out);
                entries.put(entry.getName(), out.toByteArray());
            }
        }
        return entries;
    }

    private static Map<String, byte[]> fileEntries(Map<String, byte[]> entries) {
        Map<String, byte[]> result = new LinkedHashMap<>(entries);
        result.keySet().removeIf(name -> name.endsWith("/"));
        return result;
    }

    /**
     * Checks the "language encoding" flag (bit 11) in the first local file header.
     */
    private static boolean hasUtf8Flag(Path archive) throws Exception {
        byte[] header = Arrays.copyOf(Files.readAllBytes(archive), 8);
        int flags = (header[6] & 0xFF) | (header[7] & 0xFF) << 8;
        return (flags & 0x0800) != 0;
    }

    private static byte[] slice(byte[] content, int chunk) {
        int from = chunk * CHUNK_SIZE;
        return Arrays.copyOfRange(content, from, Math.min(content.length, from + CHUNK_SIZE));
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String b64(byte[] value) {
        return Base64.getEncoder().encodeToString(value);
    }
}
