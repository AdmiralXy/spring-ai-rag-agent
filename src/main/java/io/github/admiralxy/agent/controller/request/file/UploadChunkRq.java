package io.github.admiralxy.agent.controller.request.file;

/**
 * One encrypted chunk of a file.
 *
 * @param iv   base64 of the 12-byte AES-GCM IV
 * @param data base64 of the encrypted chunk bytes with the 16-byte tag appended
 */
public record UploadChunkRq(String iv, String data) {
}
