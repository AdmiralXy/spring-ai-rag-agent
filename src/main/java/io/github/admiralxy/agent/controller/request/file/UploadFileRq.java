package io.github.admiralxy.agent.controller.request.file;

/**
 * File upload request.
 *
 * @param name      file name without extension, e.g. {@code report}
 * @param extension file extension, e.g. {@code pdf} or {@code .pdf}
 * @param content   base64 content, plain or as a data URL ({@code data:application/pdf;base64,...})
 */
public record UploadFileRq(String name, String extension, String content) {
}
