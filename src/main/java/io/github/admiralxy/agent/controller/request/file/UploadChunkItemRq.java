package io.github.admiralxy.agent.controller.request.file;

/**
 * Encrypted chunk inside a batch.
 *
 * @param fileIndex  index of the file in the session
 * @param chunkIndex index of the chunk in the file
 * @param iv         base64 of the 12-byte AES-GCM IV
 * @param data       base64 of the encrypted chunk bytes with the 16-byte tag appended
 */
public record UploadChunkItemRq(Integer fileIndex, Integer chunkIndex, String iv, String data) {
}
