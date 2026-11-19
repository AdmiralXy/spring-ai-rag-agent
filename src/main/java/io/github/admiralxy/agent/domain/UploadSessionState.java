package io.github.admiralxy.agent.domain;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * Upload session with its files and received chunk indexes (keyed by file index).
 */
public record UploadSessionState(
        UploadSession session,
        List<UploadSessionFile> files,
        Map<Integer, SortedSet<Integer>> receivedChunks
) {
}
