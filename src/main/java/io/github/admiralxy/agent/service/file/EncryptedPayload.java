package io.github.admiralxy.agent.service.file;

public record EncryptedPayload(byte[] iv, byte[] data) {
}
