package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.config.properties.FilesProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Component
public class UploadChunkStore {

    private static final Pattern CHUNK_FILE = Pattern.compile("^(\\d+)\\.(\\d+)\\.part$");
    private static final String CHUNK_NAME = "%d.%d.part";
    private static final String TEMP_NAME = "%d.%d.%s.tmp";
    private static final String ARCHIVE_FILE = "archive.tmp";
    private static final Pattern UUID_NAME =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final String CANNOT_CREATE = "Cannot create upload temp directory %s";

    private final Path root;

    public UploadChunkStore(FilesProperties properties) {
        this.root = Path.of(properties.getUploads().getTempDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException(CANNOT_CREATE.formatted(root), e);
        }
    }

    public Path writeTemp(UUID uploadId, int fileIndex, int chunkIndex, byte[] content) throws IOException {
        Path dir = Files.createDirectories(sessionDir(uploadId));
        Path tmp = dir.resolve(TEMP_NAME.formatted(fileIndex, chunkIndex, UUID.randomUUID()));
        Files.write(tmp, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return tmp;
    }

    public void commit(Path tmp, UUID uploadId, int fileIndex, int chunkIndex) throws IOException {
        Files.move(tmp, chunkPath(uploadId, fileIndex, chunkIndex), StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    public Path chunkPath(UUID uploadId, int fileIndex, int chunkIndex) {
        return sessionDir(uploadId).resolve(CHUNK_NAME.formatted(fileIndex, chunkIndex));
    }

    public Path archiveTempPath(UUID uploadId) throws IOException {
        return Files.createDirectories(sessionDir(uploadId)).resolve(ARCHIVE_FILE);
    }

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
            dirs.filter(Files::isDirectory)
                    .map(dir -> dir.getFileName().toString())
                    .filter(name -> UUID_NAME.matcher(name).matches())
                    .forEach(name -> ids.add(UUID.fromString(name)));
        }
        return ids;
    }

    public void deleteSession(UUID uploadId) {
        FileTrees.deleteQuietly(sessionDir(uploadId));
    }

    public void deleteQuietly(Path path) {
        FileTrees.deleteFileQuietly(path);
    }

    private Path sessionDir(UUID uploadId) {
        return root.resolve(uploadId.toString());
    }
}
