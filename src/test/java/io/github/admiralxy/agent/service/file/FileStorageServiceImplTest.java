package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.config.properties.FilesProperties;
import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.StoredFileContent;
import io.github.admiralxy.agent.entity.StoredFileEntity;
import io.github.admiralxy.agent.entity.StoredFileStorageRef;
import io.github.admiralxy.agent.exception.FileTooLargeException;
import io.github.admiralxy.agent.exception.InvalidFileException;
import io.github.admiralxy.agent.exception.StoredFileNotFoundException;
import io.github.admiralxy.agent.repository.StoredFileRepository;
import io.github.admiralxy.agent.service.file.FileContentStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    private static final byte[] HELLO = "hello".getBytes(StandardCharsets.UTF_8);
    private static final String HELLO_B64 = Base64.getEncoder().encodeToString(HELLO);

    @TempDir
    Path tempDir;

    private final StoredFileRepository repository = Mockito.mock(StoredFileRepository.class);
    private FileContentStore contentStore;
    private FileStorageServiceImpl service;

    @BeforeEach
    void setUp() {
        FilesProperties properties = new FilesProperties();
        properties.setMaxSize(DataSize.ofBytes(10));
        properties.setStorageDir(tempDir.resolve("files").toString());
        contentStore = new FileContentStore(properties);
        service = new FileStorageServiceImpl(repository, contentStore, properties);
    }

    @Test
    void uploadDecodesAndStoresFileInline() {
        UUID id = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(inv -> {
            StoredFileEntity e = inv.getArgument(0);
            e.setId(id);
            return e;
        });

        StoredFile file = service.upload(USER_ID, " report ", ".PDF", HELLO_B64);

        ArgumentCaptor<StoredFileEntity> captor = ArgumentCaptor.forClass(StoredFileEntity.class);
        verify(repository).save(captor.capture());
        assertArrayEquals(HELLO, captor.getValue().getContent());
        assertNull(captor.getValue().getStoragePath());
        assertEquals(id, file.id());
        assertEquals("report", file.name());
        assertEquals("pdf", file.extension());
        assertEquals("report.pdf", file.fileName());
        assertEquals("application/pdf", file.contentType());
        assertEquals(HELLO.length, file.size());
    }

    @Test
    void uploadAcceptsDataUrl() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoredFile file = service.upload(USER_ID, "note", "txt", "data:text/plain;base64," + HELLO_B64);

        assertEquals(HELLO.length, file.size());
        assertEquals("text/plain", file.contentType());
    }

    @Test
    void uploadFallsBackToOctetStreamForUnknownExtension() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoredFile file = service.upload(USER_ID, "blob", "zzzunknown", HELLO_B64);

        assertEquals("application/octet-stream", file.contentType());
    }

    @Test
    void uploadRejectsInvalidBase64() {
        assertThrows(InvalidFileException.class, () -> service.upload(USER_ID, "a", "txt", "%%%not-base64%%%"));
        verify(repository, never()).save(any());
    }

    @Test
    void uploadRejectsBadNameAndExtension() {
        assertThrows(InvalidFileException.class, () -> service.upload(USER_ID, " ", "txt", HELLO_B64));
        assertThrows(InvalidFileException.class, () -> service.upload(USER_ID, "../etc", "txt", HELLO_B64));
        assertThrows(InvalidFileException.class, () -> service.upload(USER_ID, "a", "tar.gz", HELLO_B64));
        assertThrows(InvalidFileException.class, () -> service.upload(USER_ID, "a", null, HELLO_B64));
    }

    @Test
    void uploadRejectsTooLargeFile() {
        String big = Base64.getEncoder().encodeToString(new byte[11]);

        assertThrows(FileTooLargeException.class, () -> service.upload(USER_ID, "a", "bin", big));
    }

    @Test
    void storeArchiveMovesFileToDiskStorageWithoutSizeLimit() throws Exception {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        Path archive = tempDir.resolve("archive.zip.tmp");
        byte[] body = new byte[100];
        Files.write(archive, body);

        StoredFile file = service.storeArchive(USER_ID, "Проект", archive);

        ArgumentCaptor<StoredFileEntity> captor = ArgumentCaptor.forClass(StoredFileEntity.class);
        verify(repository).save(captor.capture());
        StoredFileEntity entity = captor.getValue();
        assertNull(entity.getContent());
        assertFalse(Files.exists(archive));
        assertArrayEquals(body, Files.readAllBytes(contentStore.resolve(entity.getStoragePath())));
        assertEquals("Проект.tar.zst", file.fileName());
        assertEquals("application/zstd", file.contentType());
        assertEquals(100, file.size());
    }

    @Test
    void getAllSortsNewestFirst() {
        when(repository.findAllMetadata(any(), any())).thenReturn(Page.empty());

        service.getAll(USER_ID, 2, 50);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllMetadata(eq(USER_ID), captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(50, captor.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    void getAllRejectsInvalidPaging() {
        assertThrows(InvalidFileException.class, () -> service.getAll(USER_ID, -1, 20));
        assertThrows(InvalidFileException.class, () -> service.getAll(USER_ID, 0, 0));
        assertThrows(InvalidFileException.class, () -> service.getAll(USER_ID, 0, 101));
    }

    @Test
    void downloadReturnsInlineContent() throws Exception {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, USER_ID)).thenReturn(Optional.of(entity(id, HELLO, null)));

        StoredFileContent content = service.download(USER_ID, id);

        assertArrayEquals(HELLO, content.content().getContentAsByteArray());
        assertEquals("report.pdf", content.file().fileName());
    }

    @Test
    void downloadStreamsContentFromDisk() throws Exception {
        UUID id = UUID.randomUUID();
        Path source = tempDir.resolve("src.bin");
        Files.write(source, HELLO);
        String key = contentStore.importFile(source);
        when(repository.findByIdAndUserId(id, USER_ID)).thenReturn(Optional.of(entity(id, null, key)));

        StoredFileContent content = service.download(USER_ID, id);

        assertTrue(content.content().isFile());
        assertArrayEquals(HELLO, content.content().getContentAsByteArray());
    }

    @Test
    void downloadThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findByIdAndUserId(id, USER_ID)).thenReturn(Optional.empty());

        assertThrows(StoredFileNotFoundException.class, () -> service.download(USER_ID, id));
    }

    @Test
    void deleteThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findStorageRef(id, USER_ID)).thenReturn(Optional.empty());

        assertThrows(StoredFileNotFoundException.class, () -> service.delete(USER_ID, id));
    }

    @Test
    void deleteRemovesInlineFile() {
        UUID id = UUID.randomUUID();
        when(repository.findStorageRef(id, USER_ID)).thenReturn(Optional.of(new StoredFileStorageRef(id, null)));
        when(repository.deleteByIdDirectly(id, USER_ID)).thenReturn(1);

        service.delete(USER_ID, id);

        verify(repository).deleteByIdDirectly(id, USER_ID);
    }

    @Test
    void deleteRemovesDiskFile() throws Exception {
        UUID id = UUID.randomUUID();
        Path source = tempDir.resolve("src.bin");
        Files.write(source, HELLO);
        String key = contentStore.importFile(source);
        when(repository.findStorageRef(id, USER_ID)).thenReturn(Optional.of(new StoredFileStorageRef(id, key)));
        when(repository.deleteByIdDirectly(id, USER_ID)).thenReturn(1);

        service.delete(USER_ID, id);

        assertFalse(Files.exists(contentStore.resolve(key)));
    }

    private static StoredFileEntity entity(UUID id, byte[] content, String storagePath) {
        StoredFileEntity entity = new StoredFileEntity();
        entity.setId(id);
        entity.setName("report");
        entity.setExtension("pdf");
        entity.setContentType("application/pdf");
        entity.setSize(HELLO.length);
        entity.setContent(content);
        entity.setStoragePath(storagePath);
        return entity;
    }
}
