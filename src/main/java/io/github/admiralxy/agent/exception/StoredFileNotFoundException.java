package io.github.admiralxy.agent.exception;

import java.util.UUID;

public class StoredFileNotFoundException extends RuntimeException {

    private static final String MESSAGE = "File not found: %s";

    public StoredFileNotFoundException(UUID id) {
        super(MESSAGE.formatted(id));
    }
}
