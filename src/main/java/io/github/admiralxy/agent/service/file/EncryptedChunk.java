package io.github.admiralxy.agent.service.file;

public record EncryptedChunk(int fileIndex, int chunkIndex, EncryptedPayload payload) {
}
