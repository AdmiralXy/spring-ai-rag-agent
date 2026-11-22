package io.github.admiralxy.agent.controller.response.file;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UploadStatusRs(
        UUID uploadId,
        String keySalt,
        long chunkSize,
        Instant expiresAt,
        List<UploadSessionFileStatusRs> files
) {
}
