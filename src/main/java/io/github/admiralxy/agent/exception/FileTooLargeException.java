package io.github.admiralxy.agent.exception;

public class FileTooLargeException extends LimitExceededException {

    private static final String MESSAGE = "File exceeds the maximum allowed size of %d bytes";

    public FileTooLargeException(long maxSizeBytes) {
        super(MESSAGE.formatted(maxSizeBytes));
    }
}
