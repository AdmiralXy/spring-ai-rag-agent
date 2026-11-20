package io.github.admiralxy.agent.controller.response.file;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Upload session status.
 *
 * @param keySalt base64 of the session key salt (not secret); lets a client re-derive the session key
 *                from the password to resume an upload after a page reload
 */
public record UploadStatusRs(
        UUID uploadId,
        String keySalt,
        long chunkSize,
        Instant expiresAt,
        List<UploadSessionFileStatusRs> files
) {
}
