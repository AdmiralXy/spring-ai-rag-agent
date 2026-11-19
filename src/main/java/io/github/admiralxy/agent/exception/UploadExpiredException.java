package io.github.admiralxy.agent.exception;

import java.util.UUID;

public class UploadExpiredException extends RuntimeException {

    public UploadExpiredException(UUID uploadId) {
        super("Upload session has expired: " + uploadId);
    }
}
