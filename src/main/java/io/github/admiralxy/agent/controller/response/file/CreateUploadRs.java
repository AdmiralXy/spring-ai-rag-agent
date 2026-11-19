package io.github.admiralxy.agent.controller.response.file;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CreateUploadRs(UUID uploadId, long chunkSize, Instant expiresAt, List<UploadSessionFileRs> files) {
}
