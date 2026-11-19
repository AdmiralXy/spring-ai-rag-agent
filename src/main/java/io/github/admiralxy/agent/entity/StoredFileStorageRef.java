package io.github.admiralxy.agent.entity;

import java.util.UUID;

/**
 * Location of a file body: {@code storagePath} is null when the body is stored inline in the database.
 */
public record StoredFileStorageRef(UUID id, String storagePath) {
}
