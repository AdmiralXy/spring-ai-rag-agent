package io.github.admiralxy.agent.service;

/**
 * AES-GCM message: 12-byte IV and ciphertext with the 16-byte tag appended.
 */
public record EncryptedPayload(byte[] iv, byte[] data) {
}
