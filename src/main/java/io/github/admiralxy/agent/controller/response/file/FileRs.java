package io.github.admiralxy.agent.controller.response.file;

import io.github.admiralxy.agent.domain.StoredFile;

import java.time.Instant;
import java.util.UUID;

public record FileRs(
        UUID id,
        String name,
        String extension,
        String fileName,
        String contentType,
        long size,
        Instant createdAt
) {

    public static FileRs from(StoredFile file) {
        return new FileRs(
                file.id(),
                file.name(),
                file.extension(),
                file.fileName(),
                file.contentType(),
                file.size(),
                file.createdAt()
        );
    }
}
