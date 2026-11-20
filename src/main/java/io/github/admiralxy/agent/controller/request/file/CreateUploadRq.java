package io.github.admiralxy.agent.controller.request.file;

/**
 * Creates an encrypted chunked upload session.
 *
 * @param version encryption scheme version, must be 1
 * @param keySalt base64 of 16 random bytes; the session key is derived from it
 * @param iv      base64 of the 12-byte AES-GCM IV
 * @param data    base64 of the encrypted JSON manifest {@code {"name": ..., "files": [{"path": ..., "size": ...}]}}
 */
public record CreateUploadRq(Integer version, String keySalt, String iv, String data) {
}
