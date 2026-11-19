package io.github.admiralxy.agent.exception;

import java.io.IOException;

/**
 * Thrown while reading a request body that exceeds the configured limit.
 * Extends {@link IOException} because it is raised from the request input stream.
 */
public class RequestTooLargeException extends IOException {

    public RequestTooLargeException(long maxBytes) {
        super("Request body exceeds the maximum allowed size of " + maxBytes + " bytes");
    }
}
