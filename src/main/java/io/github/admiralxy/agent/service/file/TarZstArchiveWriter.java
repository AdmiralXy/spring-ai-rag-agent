package io.github.admiralxy.agent.service.file;

import io.airlift.compress.v3.zstd.ZstdJavaOutputStream;
import io.github.admiralxy.agent.domain.UploadSessionFile;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

public final class TarZstArchiveWriter {

    private static final int BUFFER_SIZE = 64 * 1024;
    private static final char SEPARATOR = '/';

    private TarZstArchiveWriter() {
    }

    public static void write(Path archive, List<UploadSessionFile> files,
                             BiFunction<UploadSessionFile, Integer, Path> chunkPath, Instant modified)
            throws IOException {
        Date modifiedAt = Date.from(modified);
        try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(archive), BUFFER_SIZE);
             OutputStream zstd = new ZstdJavaOutputStream(out);
             TarArchiveOutputStream tar = new TarArchiveOutputStream(zstd, StandardCharsets.UTF_8.name())) {
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            tar.setBigNumberMode(TarArchiveOutputStream.BIGNUMBER_POSIX);
            tar.setAddPaxHeadersForNonAsciiNames(true);
            Set<String> directories = new HashSet<>();
            for (UploadSessionFile file : files) {
                addParentDirectories(tar, file.path(), directories, modifiedAt);
                TarArchiveEntry entry = entry(file.path(), modifiedAt);
                entry.setSize(file.size());
                tar.putArchiveEntry(entry);
                for (int chunk = 0; chunk < file.chunkCount(); chunk++) {
                    Files.copy(chunkPath.apply(file, chunk), tar);
                }
                tar.closeArchiveEntry();
            }
            tar.finish();
        }
    }

    private static void addParentDirectories(TarArchiveOutputStream tar, String path, Set<String> directories,
                                             Date modified) throws IOException {
        for (int slash = path.indexOf(SEPARATOR); slash > 0; slash = path.indexOf(SEPARATOR, slash + 1)) {
            String directory = path.substring(0, slash + 1);
            if (directories.add(directory)) {
                tar.putArchiveEntry(entry(directory, modified));
                tar.closeArchiveEntry();
            }
        }
    }

    private static TarArchiveEntry entry(String name, Date modified) {
        TarArchiveEntry entry = new TarArchiveEntry(name);
        entry.setUserName(StringUtils.EMPTY);
        entry.setGroupName(StringUtils.EMPTY);
        entry.setUserId(0);
        entry.setGroupId(0);
        entry.setModTime(modified);
        return entry;
    }
}
