package io.github.admiralxy.agent.exception;

public class UploadDecryptionException extends RuntimeException {

    private static final String MESSAGE = "Decryption failed: wrong upload password or corrupted data";

    public UploadDecryptionException() {
        super(MESSAGE);
    }
}
