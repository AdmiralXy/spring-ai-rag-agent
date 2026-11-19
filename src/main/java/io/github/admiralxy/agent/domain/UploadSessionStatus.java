package io.github.admiralxy.agent.domain;

public enum UploadSessionStatus {

    /**
     * Accepting chunks.
     */
    ACTIVE,

    /**
     * Archive is being built; chunks are no longer accepted.
     */
    COMPLETING
}
