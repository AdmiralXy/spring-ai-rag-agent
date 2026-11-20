package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.config.properties.FilesProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Temporary disk storage for upload chunks.
 *
 * <p>Layout: {@code <temp-dir>/<uploadId>/<fileIndex>.<chunkIndex>.part}. A chunk is written to a unique
 * temporary file and then atomically renamed, so a chunk file that exists is always complete and
 * "received" is simply "the file exists".
 */
@Slf4j
@Component
public class UploadChunkStore {

    private static final Pattern CHUNK_FILE = Pattern.compile("^(\\d+)\\.(\\d+)\\.part$");
    private static final String ARCHIVE_FILE = "archive.tmp";

    private final Path root;

    public UploadChunkStore(FilesProperties properties) {
        this.root = Path.of(properties.getUploads().getTempDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create upload temp directory " + root, e);
        }
    }

    public Path writeTemp(UUID uploadId, int fileIndex, int chunkIndex, byte[] content) throws IOException {
        Path dir = Files.createDirectories(sessionDir(uploadId));
        Path tmp = dir.resolve(fileIndex + "." + chunkIndex + "." + UUID.randomUUID() + ".tmp");
        Files.write(tmp, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return tmp;
    }

    /**
     * Atomically publishes a written chunk, replacing a previously received copy.
     */
    public void commit(Path tmp, UUID uploadId, int fileIndex, int chunkIndex) throws IOException {
        Files.move(tmp, chunkPath(uploadId, fileIndex, chunkIndex), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    public Path chunkPath(UUID uploadId, int fileIndex, int chunkIndex) {
        return sessionDir(uploadId).resolve(fileIndex + "." + chunkIndex + ".part");
    }

    public Path archiveTempPath(UUID uploadId) throws IOException {
        return Files.createDirectories(sessionDir(uploadId)).resolve(ARCHIVE_FILE);
    }

    /**
     * @return received chunk indexes grouped by file index
     */
    public Map<Integer, SortedSet<Integer>> listReceived(UUID uploadId) throws IOException {
        Path dir = sessionDir(uploadId);
        Map<Integer, SortedSet<Integer>> received = new HashMap<>();
        if (!Files.isDirectory(dir)) {
            return received;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(file -> {
                Matcher m = CHUNK_FILE.matcher(file.getFileName().toString());
                if (m.matches()) {
                    received.computeIfAbsent(Integer.parseInt(m.group(1)), k -> new TreeSet<>())
                            .add(Integer.parseInt(m.group(2)));
                }
            });
        }
        return received;
    }

    public List<UUID> listSessionIds() throws IOException {
        List<UUID> ids = new ArrayList<>();
        try (Stream<Path> dirs = Files.list(root)) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                try {
                    ids.add(UUID.fromString(dir.getFileName().toString()));
                } catch (IllegalArgumentException ignored) {
                    // not a session directory
                }
            });
        }
        return ids;
    }

    public void deleteSession(UUID uploadId) {
        Path dir = sessionDir(uploadId);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
        } catch (IOException e) {
            log.warn("Failed to delete upload directory {}", dir, e);
        }
    }

    public void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Failed to delete {}", path, e);
        }
    }

    private Path sessionDir(UUID uploadId) {
        return root.resolve(uploadId.toString());
    }
}
