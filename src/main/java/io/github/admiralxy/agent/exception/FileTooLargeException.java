package io.github.admiralxy.agent.exception;

public class FileTooLargeException extends LimitExceededException {

    public FileTooLargeException(long maxSizeBytes) {
        super("File exceeds the maximum allowed size of " + maxSizeBytes + " bytes");
    }
}
