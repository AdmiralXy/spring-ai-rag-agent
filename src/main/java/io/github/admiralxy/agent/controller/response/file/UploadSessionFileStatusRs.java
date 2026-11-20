package io.github.admiralxy.agent.controller.response.file;

import java.util.List;

public record UploadSessionFileStatusRs(int index, long size, int chunkCount, List<Integer> receivedChunks) {
}
