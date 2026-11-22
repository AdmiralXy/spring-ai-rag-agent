package io.github.admiralxy.agent.exception;

import java.io.IOException;

public class RequestTooLargeException extends IOException {

    private static final String MESSAGE = "Request body exceeds the maximum allowed size of %d bytes";

    public RequestTooLargeException(long maxBytes) {
        super(MESSAGE.formatted(maxBytes));
    }
}
