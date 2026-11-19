package io.github.admiralxy.agent.domain;

import java.time.Instant;
import java.util.UUID;

public record StoredFile(UUID id, String name, String extension, String contentType, long size, Instant createdAt) {

    public String fileName() {
        return name + "." + extension;
    }
}
