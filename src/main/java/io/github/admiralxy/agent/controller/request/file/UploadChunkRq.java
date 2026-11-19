package io.github.admiralxy.agent.controller.request.file;

/**
 * One chunk of a file.
 *
 * @param content base64 of the chunk bytes, plain or as a data URL
 */
public record UploadChunkRq(String content) {
}
