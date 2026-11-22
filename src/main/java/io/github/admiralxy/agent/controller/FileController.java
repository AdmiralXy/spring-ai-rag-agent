package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.file.UploadFileRq;
import io.github.admiralxy.agent.controller.response.file.FileRs;
import io.github.admiralxy.agent.controller.response.file.GetFilesRs;
import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.domain.StoredFileContent;
import io.github.admiralxy.agent.security.AuthUser;
import io.github.admiralxy.agent.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/files")
public class FileController {

    private final FileStorageService fileStorageService;

    @GetMapping
    public GetFilesRs getAll(@AuthenticationPrincipal AuthUser user, @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "20") int size) {
        return new GetFilesRs(fileStorageService.getAll(user.id(), page, size).map(FileRs::from));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FileRs upload(@AuthenticationPrincipal AuthUser user, @RequestBody UploadFileRq rq) {
        return FileRs.from(fileStorageService.upload(user.id(), rq.name(), rq.extension(), rq.content()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        StoredFileContent stored = fileStorageService.download(user.id(), id);
        StoredFile file = stored.file();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(file.fileName(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(stored.content());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        fileStorageService.delete(user.id(), id);
    }
}
