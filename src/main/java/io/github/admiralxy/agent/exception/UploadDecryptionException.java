package io.github.admiralxy.agent.exception;

/**
 * Encrypted upload data could not be authenticated: wrong password, wrong context or tampered data (HTTP 401).
 */
public class UploadDecryptionException extends RuntimeException {

    public UploadDecryptionException() {
        super("Decryption failed: wrong upload password or corrupted data");
    }
}
