package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.StoredFileContent;
import io.github.admiralxy.agent.entity.StoredFileEntity;
import io.github.admiralxy.agent.exception.FileTooLargeException;
import io.github.admiralxy.agent.exception.InvalidFileException;
import io.github.admiralxy.agent.exception.StoredFileNotFoundException;
import io.github.admiralxy.agent.repository.StoredFileRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.unit.DataSize;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileStorageServiceImplTest {

    private static final byte[] HELLO = "hello".getBytes(StandardCharsets.UTF_8);
    private static final String HELLO_B64 = Base64.getEncoder().encodeToString(HELLO);

    private final StoredFileRepository repository = Mockito.mock(StoredFileRepository.class);
    private final FileStorageServiceImpl service = new FileStorageServiceImpl(repository, DataSize.ofBytes(10));

    @Test
    void uploadDecodesAndStoresFile() {
        UUID id = UUID.randomUUID();
        when(repository.save(any())).thenAnswer(inv -> {
            StoredFileEntity e = inv.getArgument(0);
            e.setId(id);
            return e;
        });

        StoredFile file = service.upload(" report ", ".PDF", HELLO_B64);

        ArgumentCaptor<StoredFileEntity> captor = ArgumentCaptor.forClass(StoredFileEntity.class);
        verify(repository).save(captor.capture());
        assertArrayEquals(HELLO, captor.getValue().getContent());
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

        StoredFile file = service.upload("note", "txt", "data:text/plain;base64," + HELLO_B64);

        assertEquals(HELLO.length, file.size());
        assertEquals("text/plain", file.contentType());
    }

    @Test
    void uploadFallsBackToOctetStreamForUnknownExtension() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        StoredFile file = service.upload("blob", "zzzunknown", HELLO_B64);

        assertEquals("application/octet-stream", file.contentType());
    }

    @Test
    void uploadRejectsInvalidBase64() {
        assertThrows(InvalidFileException.class, () -> service.upload("a", "txt", "%%%not-base64%%%"));
        verify(repository, never()).save(any());
    }

    @Test
    void uploadRejectsBadNameAndExtension() {
        assertThrows(InvalidFileException.class, () -> service.upload(" ", "txt", HELLO_B64));
        assertThrows(InvalidFileException.class, () -> service.upload("../etc", "txt", HELLO_B64));
        assertThrows(InvalidFileException.class, () -> service.upload("a", "tar.gz", HELLO_B64));
        assertThrows(InvalidFileException.class, () -> service.upload("a", null, HELLO_B64));
    }

    @Test
    void uploadRejectsTooLargeFile() {
        String big = Base64.getEncoder().encodeToString(new byte[11]);

        assertThrows(FileTooLargeException.class, () -> service.upload("a", "bin", big));
    }

    @Test
    void getAllSortsNewestFirst() {
        when(repository.findAllMetadata(any())).thenReturn(Page.empty());

        service.getAll(2, 50);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllMetadata(captor.capture());
        assertEquals(2, captor.getValue().getPageNumber());
        assertEquals(50, captor.getValue().getPageSize());
        assertEquals(Sort.Direction.DESC, captor.getValue().getSort().getOrderFor("createdAt").getDirection());
    }

    @Test
    void getAllRejectsInvalidPaging() {
        assertThrows(InvalidFileException.class, () -> service.getAll(-1, 20));
        assertThrows(InvalidFileException.class, () -> service.getAll(0, 0));
        assertThrows(InvalidFileException.class, () -> service.getAll(0, 101));
    }

    @Test
    void downloadReturnsContent() {
        UUID id = UUID.randomUUID();
        StoredFileEntity entity = new StoredFileEntity();
        entity.setId(id);
        entity.setName("report");
        entity.setExtension("pdf");
        entity.setContentType("application/pdf");
        entity.setSize(HELLO.length);
        entity.setContent(HELLO);
        when(repository.findById(id)).thenReturn(Optional.of(entity));

        StoredFileContent content = service.download(id);

        assertArrayEquals(HELLO, content.content());
        assertEquals("report.pdf", content.file().fileName());
    }

    @Test
    void downloadThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThrows(StoredFileNotFoundException.class, () -> service.download(id));
    }

    @Test
    void deleteThrowsWhenMissing() {
        UUID id = UUID.randomUUID();
        when(repository.deleteByIdDirectly(id)).thenReturn(0);

        assertThrows(StoredFileNotFoundException.class, () -> service.delete(id));
    }

    @Test
    void deleteRemovesFile() {
        UUID id = UUID.randomUUID();
        when(repository.deleteByIdDirectly(id)).thenReturn(1);

        service.delete(id);

        verify(repository).deleteByIdDirectly(id);
    }
}
