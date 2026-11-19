package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.file.CreateUploadFileRq;
import io.github.admiralxy.agent.controller.request.file.CreateUploadRq;
import io.github.admiralxy.agent.controller.request.file.UploadChunkRq;
import io.github.admiralxy.agent.controller.response.file.CreateUploadRs;
import io.github.admiralxy.agent.controller.response.file.FileRs;
import io.github.admiralxy.agent.controller.response.file.UploadSessionFileRs;
import io.github.admiralxy.agent.controller.response.file.UploadSessionFileStatusRs;
import io.github.admiralxy.agent.controller.response.file.UploadStatusRs;
import io.github.admiralxy.agent.domain.UploadSessionFile;
import io.github.admiralxy.agent.domain.UploadSessionState;
import io.github.admiralxy.agent.service.UploadFileSpec;
import io.github.admiralxy.agent.service.UploadSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Chunked upload of folders packed into a ZIP archive.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/files/uploads")
public class FileUploadController {

    private final UploadSessionService uploadSessionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUploadRs create(@RequestBody CreateUploadRq rq) {
        List<UploadFileSpec> specs = rq.files() == null ? null : rq.files().stream()
                .map(FileUploadController::toSpec)
                .toList();
        UploadSessionState state = uploadSessionService.create(rq.name(), specs);
        return new CreateUploadRs(
                state.session().id(),
                state.session().chunkSize(),
                state.session().expiresAt(),
                state.files().stream().map(FileUploadController::toFileRs).toList()
        );
    }

    @PutMapping("/{uploadId}/files/{fileIndex}/chunks/{chunkIndex}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadChunk(
            @PathVariable UUID uploadId,
            @PathVariable int fileIndex,
            @PathVariable int chunkIndex,
            @RequestBody UploadChunkRq rq
    ) {
        uploadSessionService.putChunk(uploadId, fileIndex, chunkIndex, rq.content());
    }

    @GetMapping("/{uploadId}")
    public UploadStatusRs getStatus(@PathVariable UUID uploadId) {
        UploadSessionState state = uploadSessionService.getStatus(uploadId);
        List<UploadSessionFileStatusRs> files = state.files().stream()
                .map(file -> new UploadSessionFileStatusRs(
                        file.index(),
                        file.path(),
                        file.size(),
                        file.chunkCount(),
                        state.receivedChunks().getOrDefault(file.index(), Collections.emptySortedSet()).stream()
                                .filter(chunk -> chunk < file.chunkCount())
                                .toList()
                ))
                .toList();
        return new UploadStatusRs(
                state.session().id(),
                state.session().name(),
                state.session().chunkSize(),
                state.session().expiresAt(),
                files
        );
    }

    @PostMapping("/{uploadId}/complete")
    @ResponseStatus(HttpStatus.CREATED)
    public FileRs complete(@PathVariable UUID uploadId) {
        return FileRs.from(uploadSessionService.complete(uploadId));
    }

    @DeleteMapping("/{uploadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID uploadId) {
        uploadSessionService.cancel(uploadId);
    }

    private static UploadFileSpec toSpec(CreateUploadFileRq file) {
        return file == null ? null : new UploadFileSpec(file.path(), file.size());
    }

    private static UploadSessionFileRs toFileRs(UploadSessionFile file) {
        return new UploadSessionFileRs(file.index(), file.path(), file.size(), file.chunkCount());
    }
}
