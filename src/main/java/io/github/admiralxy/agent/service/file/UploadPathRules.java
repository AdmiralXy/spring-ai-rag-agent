package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.exception.InvalidFileException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Validation rules for relative paths of files inside an uploaded folder archive.
 * Error messages refer to files by index only: paths are confidential and must not appear in plaintext responses.
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
        String field = field(index);
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
     *
     * @param paths normalized paths in request order
     */
    public static void checkConflicts(List<String> paths) {
        Map<String, Integer> files = new HashMap<>();
        for (int i = 0; i < paths.size(); i++) {
            Integer previous = files.putIfAbsent(paths.get(i).toLowerCase(Locale.ROOT), i);
            if (previous != null) {
                throw new InvalidFileException(field(i) + " duplicates 'files[" + previous + "].path' (case-insensitive)");
            }
        }
        for (int i = 0; i < paths.size(); i++) {
            String lower = paths.get(i).toLowerCase(Locale.ROOT);
            for (int slash = lower.indexOf('/'); slash > 0; slash = lower.indexOf('/', slash + 1)) {
                Integer parent = files.get(lower.substring(0, slash));
                if (parent != null) {
                    throw new InvalidFileException(field(i) + " uses 'files[" + parent + "].path' as a folder, but it is a file");
                }
            }
        }
    }

    private static String field(int index) {
        return "Field 'files[" + index + "].path'";
    }
}
