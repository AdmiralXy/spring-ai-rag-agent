package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.file.UploadFileRq;
import io.github.admiralxy.agent.controller.response.file.FileRs;
import io.github.admiralxy.agent.controller.response.file.GetFilesRs;
import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.StoredFileContent;
import io.github.admiralxy.agent.service.file.FileStorageService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

class FileControllerTest {

    private final FileStorageService fileStorageService = Mockito.mock(FileStorageService.class);
    private final FileController fileController = new FileController(fileStorageService);

    @Test
    void uploadReturnsMetadata() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        UploadFileRq rq = new UploadFileRq("report", "pdf", "aGVsbG8=");
        Mockito.when(fileStorageService.upload("report", "pdf", "aGVsbG8="))
                .thenReturn(new StoredFile(id, "report", "pdf", "application/pdf", 5, now));

        FileRs rs = fileController.upload(rq);

        assertEquals(id, rs.id());
        assertEquals("report.pdf", rs.fileName());
        assertEquals("application/pdf", rs.contentType());
        assertEquals(5, rs.size());
        assertEquals(now, rs.createdAt());
    }

    @Test
    void downloadReturnsBinaryWithAttachmentHeader() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);
        StoredFile file = new StoredFile(id, "отчёт", "pdf", "application/pdf", bytes.length, Instant.now());
        Mockito.when(fileStorageService.download(id)).thenReturn(new StoredFileContent(file, new ByteArrayResource(bytes)));

        ResponseEntity<Resource> rs = fileController.download(id);

        assertArrayEquals(bytes, rs.getBody().getContentAsByteArray());
        assertEquals(MediaType.APPLICATION_PDF, rs.getHeaders().getContentType());
        String disposition = rs.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("attachment;"));
        assertTrue(disposition.contains("filename*=UTF-8''"));
    }

    @Test
    void getAllReturnsPageOfMetadata() {
        UUID id = UUID.randomUUID();
        StoredFile file = new StoredFile(id, "report", "pdf", "application/pdf", 5, Instant.now());
        Mockito.when(fileStorageService.getAll(0, 20))
                .thenReturn(new PageImpl<>(List.of(file), PageRequest.of(0, 20), 1));

        GetFilesRs rs = fileController.getAll(0, 20);

        assertEquals(1, rs.files().getTotalElements());
        assertEquals(id, rs.files().getContent().getFirst().id());
        assertEquals("report.pdf", rs.files().getContent().getFirst().fileName());
    }

    @Test
    void deleteDelegatesToService() {
        UUID id = UUID.randomUUID();

        fileController.delete(id);

        verify(fileStorageService).delete(id);
    }
}
