package io.github.admiralxy.agent.controller.request.file;

import java.util.List;

public record UploadChunksRq(List<UploadChunkItemRq> chunks) {
}
