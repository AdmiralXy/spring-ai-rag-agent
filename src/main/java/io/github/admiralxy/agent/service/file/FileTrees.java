package io.github.admiralxy.agent.service.file;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

@Slf4j
public final class FileTrees {

    private static final String DELETE_TREE_FAILED = "Failed to delete directory {}";
    private static final String DELETE_FAILED = "Failed to delete {}";

    private FileTrees() {
    }

    public static void deleteQuietly(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(FileTrees::deleteFileQuietly);
        } catch (IOException e) {
            log.warn(DELETE_TREE_FAILED, directory, e);
        }
    }

    public static void deleteFileQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn(DELETE_FAILED, path, e);
        }
    }
}
