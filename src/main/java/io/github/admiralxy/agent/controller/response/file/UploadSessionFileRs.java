package io.github.admiralxy.agent.controller.response.file;

public record UploadSessionFileRs(int index, String path, long size, int chunkCount) {
}
