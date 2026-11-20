package io.github.admiralxy.agent.controller.response.file;

/**
 * File of an upload session. The path is not returned: it only travels encrypted, the client maps indexes itself.
 */
public record UploadSessionFileRs(int index, long size, int chunkCount) {
}
