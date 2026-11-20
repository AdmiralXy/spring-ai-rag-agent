package io.github.admiralxy.agent.controller.request.file;

import java.util.List;

/**
 * Batch of encrypted chunks, typically many small files in one request.
 */
public record UploadChunksRq(List<UploadChunkItemRq> chunks) {
}
