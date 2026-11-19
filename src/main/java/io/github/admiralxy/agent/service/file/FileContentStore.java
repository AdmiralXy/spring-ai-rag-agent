package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.config.properties.FilesProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Disk storage for file bodies. Files are addressed by an opaque key relative to the storage directory.
 */
@Slf4j
@Component
public class FileContentStore {

    private final Path root;

    public FileContentStore(FilesProperties properties) {
        this.root = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create file storage directory " + root, e);
        }
    }

    /**
     * Moves the source file into the storage.
     *
     * @return storage key of the imported file
     */
    public String importFile(Path source) throws IOException {
        String key = UUID.randomUUID().toString();
        Path target = root.resolve(key);
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, target);
        }
        return key;
    }

    public Path resolve(String key) {
        // Keys are always UUIDs; parsing guards against path traversal through corrupted data.
        return root.resolve(UUID.fromString(key).toString());
    }

    public void deleteQuietly(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("Failed to delete stored file {}", key, e);
        }
    }
}
