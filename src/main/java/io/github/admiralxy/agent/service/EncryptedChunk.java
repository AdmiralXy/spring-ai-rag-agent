package io.github.admiralxy.agent.service;

/**
 * Encrypted chunk of a file in an upload session.
 */
public record EncryptedChunk(int fileIndex, int chunkIndex, EncryptedPayload payload) {
}
