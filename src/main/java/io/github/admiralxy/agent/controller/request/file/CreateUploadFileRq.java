package io.github.admiralxy.agent.controller.request.file;

/**
 * File declared in an upload session.
 *
 * @param path relative path inside the archive, {@code /}-separated
 * @param size size in bytes, {@code >= 0}
 */
public record CreateUploadFileRq(String path, Long size) {
}
