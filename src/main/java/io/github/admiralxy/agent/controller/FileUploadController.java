package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.config.properties.FilesProperties;
import io.github.admiralxy.agent.controller.request.file.CreateUploadRq;
import io.github.admiralxy.agent.controller.request.file.UploadChunkItemRq;
import io.github.admiralxy.agent.controller.request.file.UploadChunkRq;
import io.github.admiralxy.agent.controller.request.file.UploadChunksRq;
import io.github.admiralxy.agent.controller.response.file.CreateUploadRs;
import io.github.admiralxy.agent.controller.response.file.FileRs;
import io.github.admiralxy.agent.controller.response.file.UploadConfigRs;
import io.github.admiralxy.agent.controller.response.file.UploadSessionFileRs;
import io.github.admiralxy.agent.controller.response.file.UploadSessionFileStatusRs;
import io.github.admiralxy.agent.controller.response.file.UploadStatusRs;
import io.github.admiralxy.agent.domain.UploadSessionState;
import io.github.admiralxy.agent.exception.InvalidFileException;
import io.github.admiralxy.agent.service.EncryptedChunk;
import io.github.admiralxy.agent.service.EncryptedPayload;
import io.github.admiralxy.agent.service.UploadSessionService;
import io.github.admiralxy.agent.service.file.Base64Content;
import io.github.admiralxy.agent.service.file.UploadCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Encrypted chunked upload of folders packed into a .tar.zst archive.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/files/uploads")
public class FileUploadController {

    static final String PROOF_HEADER = "X-Upload-Proof";

    private final UploadSessionService uploadSessionService;
    private final UploadCrypto uploadCrypto;
    private final FilesProperties filesProperties;

    @GetMapping("/config")
    public UploadConfigRs getConfig() {
        FilesProperties.Uploads uploads = filesProperties.getUploads();
        return new UploadConfigRs(
                uploads.getChunkSize().toBytes(),
                uploads.getMaxTotalSize().toBytes(),
                uploads.getMaxFiles(),
                uploads.getMaxRequestSize().toBytes(),
                uploads.getMaxBatchChunks(),
                new UploadConfigRs.UploadEncryptionRs(
                        UploadCrypto.VERSION,
                        "PBKDF2-SHA256",
                        uploadCrypto.kdfIterations(),
                        uploadCrypto.kdfSalt(),
                        "HKDF-SHA256",
                        UploadCrypto.HKDF_INFO,
                        "AES-256-GCM",
                        UploadCrypto.KEY_SALT_LENGTH,
                        UploadCrypto.IV_LENGTH,
                        UploadCrypto.TAG_LENGTH
                )
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateUploadRs create(@RequestBody CreateUploadRq rq) {
        if (rq.version() == null || rq.version() != UploadCrypto.VERSION) {
            throw new InvalidFileException("Field 'version' must be " + UploadCrypto.VERSION);
        }
        byte[] keySalt = Base64Content.decodeField(rq.keySalt(), "keySalt");
        UploadSessionState state = uploadSessionService.create(keySalt, payload(rq.iv(), rq.data(), ""));
        return new CreateUploadRs(
                state.session().id(),
                state.session().chunkSize(),
                state.session().expiresAt(),
                state.files().stream()
                        .map(file -> new UploadSessionFileRs(file.index(), file.size(), file.chunkCount()))
                        .toList()
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
        uploadSessionService.putChunk(uploadId, new EncryptedChunk(fileIndex, chunkIndex, payload(rq.iv(), rq.data(), "")));
    }

    @PostMapping("/{uploadId}/chunks")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadChunks(@PathVariable UUID uploadId, @RequestBody UploadChunksRq rq) {
        if (rq.chunks() == null) {
            throw new InvalidFileException("Field 'chunks' must not be empty");
        }
        List<EncryptedChunk> chunks = new ArrayList<>(rq.chunks().size());
        for (int i = 0; i < rq.chunks().size(); i++) {
            UploadChunkItemRq item = rq.chunks().get(i);
            String prefix = "chunks[" + i + "].";
            if (item == null || item.fileIndex() == null || item.chunkIndex() == null) {
                throw new InvalidFileException("Fields '" + prefix + "fileIndex' and '" + prefix + "chunkIndex' are required");
            }
            chunks.add(new EncryptedChunk(item.fileIndex(), item.chunkIndex(), payload(item.iv(), item.data(), prefix)));
        }
        uploadSessionService.putChunks(uploadId, chunks);
    }

    @GetMapping("/{uploadId}")
    public UploadStatusRs getStatus(@PathVariable UUID uploadId) {
        UploadSessionState state = uploadSessionService.getStatus(uploadId);
        List<UploadSessionFileStatusRs> files = state.files().stream()
                .map(file -> new UploadSessionFileStatusRs(
                        file.index(),
                        file.size(),
                        file.chunkCount(),
                        state.receivedChunks().getOrDefault(file.index(), Collections.emptySortedSet()).stream()
                                .filter(chunk -> chunk < file.chunkCount())
                                .toList()
                ))
                .toList();
        return new UploadStatusRs(
                state.session().id(),
                Base64.getEncoder().encodeToString(state.session().keySalt()),
                state.session().chunkSize(),
                state.session().expiresAt(),
                files
        );
    }

    @PostMapping("/{uploadId}/complete")
    @ResponseStatus(HttpStatus.CREATED)
    public FileRs complete(@PathVariable UUID uploadId, @RequestHeader(PROOF_HEADER) String proof) {
        return FileRs.from(uploadSessionService.complete(uploadId, parseProof(proof)));
    }

    @DeleteMapping("/{uploadId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable UUID uploadId, @RequestHeader(PROOF_HEADER) String proof) {
        uploadSessionService.cancel(uploadId, parseProof(proof));
    }

    private static EncryptedPayload payload(String iv, String data, String fieldPrefix) {
        return new EncryptedPayload(
                Base64Content.decodeField(iv, fieldPrefix + "iv"),
                Base64Content.decodeField(data, fieldPrefix + "data")
        );
    }

    /**
     * Proof format: {@code base64(iv).base64(ciphertext)}.
     */
    private static EncryptedPayload parseProof(String proof) {
        int dot = proof.indexOf('.');
        if (dot <= 0 || dot == proof.length() - 1) {
            throw new InvalidFileException("Header '" + PROOF_HEADER + "' must be '<base64 iv>.<base64 data>'");
        }
        return payload(proof.substring(0, dot), proof.substring(dot + 1), PROOF_HEADER + ".");
    }
}
