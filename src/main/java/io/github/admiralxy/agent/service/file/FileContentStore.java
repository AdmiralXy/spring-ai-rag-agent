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

@Slf4j
@Component
public class FileContentStore {

    private static final String CANNOT_CREATE = "Cannot create file storage directory %s";
    private static final String DELETE_FAILED = "Failed to delete stored file {}";

    private final Path root;

    public FileContentStore(FilesProperties properties) {
        this.root = Path.of(properties.getStorageDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(CANNOT_CREATE.formatted(root), e);
        }
    }

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
        return root.resolve(UUID.fromString(key).toString());
    }

    public void deleteQuietly(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException | IllegalArgumentException e) {
            log.warn(DELETE_FAILED, key, e);
        }
    }
}
