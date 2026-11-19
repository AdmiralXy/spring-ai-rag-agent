package io.github.admiralxy.agent.service;

/**
 * File declared when an upload session is created.
 *
 * @param path relative path inside the archive
 * @param size size in bytes
 */
public record UploadFileSpec(String path, Long size) {
}
