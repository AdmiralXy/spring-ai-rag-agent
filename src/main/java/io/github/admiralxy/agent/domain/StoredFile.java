package io.github.admiralxy.agent.domain;

import java.time.Instant;
import java.util.UUID;

public record StoredFile(UUID id, String name, String extension, String contentType, long size, Instant createdAt) {

    private static final String FILE_NAME = "%s.%s";

    public String fileName() {
        return FILE_NAME.formatted(name, extension);
    }
}
