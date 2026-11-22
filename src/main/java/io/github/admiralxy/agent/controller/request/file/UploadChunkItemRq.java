package io.github.admiralxy.agent.controller.request.file;

public record UploadChunkItemRq(Integer fileIndex, Integer chunkIndex, String iv, String data) {
}
