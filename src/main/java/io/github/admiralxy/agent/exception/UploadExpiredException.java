package io.github.admiralxy.agent.exception;

import java.util.UUID;

public class UploadExpiredException extends RuntimeException {

    private static final String MESSAGE = "Upload session has expired: %s";

    public UploadExpiredException(UUID uploadId) {
        super(MESSAGE.formatted(uploadId));
    }
}
