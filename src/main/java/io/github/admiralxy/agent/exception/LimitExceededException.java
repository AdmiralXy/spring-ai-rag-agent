package io.github.admiralxy.agent.exception;

/**
 * A configured size or count limit was exceeded (HTTP 413).
 */
public class LimitExceededException extends RuntimeException {

    public LimitExceededException(String message) {
        super(message);
    }
}
