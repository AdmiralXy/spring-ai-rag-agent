package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.exception.InvalidFileException;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validation rules for relative paths of files inside an uploaded folder archive.
 */
public final class UploadPathRules {

    public static final int MAX_PATH_LENGTH = 1024;

    private static final Pattern DRIVE_LETTER = Pattern.compile("^[A-Za-z]:");
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");

    private UploadPathRules() {
    }

    /**
     * Normalizes separators to {@code /} and rejects absolute or unsafe paths.
     *
     * @param index position of the file in the request, used in error messages
     */
    public static String normalize(String rawPath, int index) {
        String field = "Field 'files[" + index + "].path'";
        if (rawPath == null || rawPath.isEmpty()) {
            throw new InvalidFileException(field + " is required");
        }
        String path = rawPath.replace('\\', '/');
        if (path.length() > MAX_PATH_LENGTH) {
            throw new InvalidFileException(field + " must be at most " + MAX_PATH_LENGTH + " characters");
        }
        if (CONTROL_CHARS.matcher(path).find()) {
            throw new InvalidFileException(field + " must not contain control characters");
        }
        if (path.startsWith("/")) {
            throw new InvalidFileException(field + " must be relative, not start with '/'");
        }
        if (DRIVE_LETTER.matcher(path).find()) {
            throw new InvalidFileException(field + " must not start with a drive letter");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty()) {
                throw new InvalidFileException(field + " must not contain empty segments");
            }
            if (".".equals(segment) || "..".equals(segment)) {
                throw new InvalidFileException(field + " must not contain '.' or '..' segments");
            }
        }
        return path;
    }

    /**
     * Rejects case-insensitive duplicates and paths where a file is also used as a folder.
     */
    public static void checkConflicts(List<String> paths) {
        Set<String> files = new HashSet<>();
        for (String path : paths) {
            if (!files.add(path.toLowerCase(Locale.ROOT))) {
                throw new InvalidFileException("Duplicate path (case-insensitive): '" + path + "'");
            }
        }
        for (String path : paths) {
            String lower = path.toLowerCase(Locale.ROOT);
            for (int slash = lower.indexOf('/'); slash > 0; slash = lower.indexOf('/', slash + 1)) {
                String parent = lower.substring(0, slash);
                if (files.contains(parent)) {
                    throw new InvalidFileException("Path '" + path + "' uses '" + parent + "' as a folder, but it is also a file");
                }
            }
        }
    }
}
