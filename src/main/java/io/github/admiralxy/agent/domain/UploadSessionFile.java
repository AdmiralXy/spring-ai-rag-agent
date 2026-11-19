package io.github.admiralxy.agent.domain;

public record UploadSessionFile(int index, String path, long size, int chunkCount) {
}
