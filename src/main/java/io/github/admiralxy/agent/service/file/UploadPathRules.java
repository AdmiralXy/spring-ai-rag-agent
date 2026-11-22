package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.exception.InvalidFileException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public final class UploadPathRules {

    public static final int MAX_PATH_LENGTH = 1024;

    private static final Pattern DRIVE_LETTER = Pattern.compile("^[A-Za-z]:");
    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");
    private static final Set<String> RELATIVE_SEGMENTS = Set.of(".", "..");
    private static final String SEPARATOR = "/";
    private static final char SEPARATOR_CHAR = '/';
    private static final char WINDOWS_SEPARATOR = '\\';
    private static final String FIELD = "Field 'files[%d].path'";
    private static final String REQUIRED = "%s is required";
    private static final String TOO_LONG = "%s must be at most %d characters";
    private static final String CONTROL = "%s must not contain control characters";
    private static final String ABSOLUTE = "%s must be relative, not start with '/'";
    private static final String DRIVE = "%s must not start with a drive letter";
    private static final String EMPTY_SEGMENT = "%s must not contain empty segments";
    private static final String DOT_SEGMENT = "%s must not contain '.' or '..' segments";
    private static final String DUPLICATE = "%s duplicates 'files[%d].path' (case-insensitive)";
    private static final String FILE_AS_FOLDER = "%s uses 'files[%d].path' as a folder, but it is a file";

    private UploadPathRules() {
    }

    public static String normalize(String rawPath, int index) {
        String field = FIELD.formatted(index);
        if (rawPath == null || rawPath.isEmpty()) {
            throw invalid(REQUIRED, field);
        }
        String path = rawPath.replace(WINDOWS_SEPARATOR, SEPARATOR_CHAR);
        if (path.length() > MAX_PATH_LENGTH) {
            throw new InvalidFileException(TOO_LONG.formatted(field, MAX_PATH_LENGTH));
        }
        if (CONTROL_CHARS.matcher(path).find()) {
            throw invalid(CONTROL, field);
        }
        if (path.startsWith(SEPARATOR)) {
            throw invalid(ABSOLUTE, field);
        }
        if (DRIVE_LETTER.matcher(path).find()) {
            throw invalid(DRIVE, field);
        }
        for (String segment : path.split(SEPARATOR, -1)) {
            if (segment.isEmpty()) {
                throw invalid(EMPTY_SEGMENT, field);
            }
            if (RELATIVE_SEGMENTS.contains(segment)) {
                throw invalid(DOT_SEGMENT, field);
            }
        }
        return path;
    }

    public static void checkConflicts(List<String> paths) {
        Map<String, Integer> files = new HashMap<>();
        for (int i = 0; i < paths.size(); i++) {
            Integer previous = files.putIfAbsent(paths.get(i).toLowerCase(Locale.ROOT), i);
            if (previous != null) {
                throw new InvalidFileException(DUPLICATE.formatted(FIELD.formatted(i), previous));
            }
        }
        for (int i = 0; i < paths.size(); i++) {
            String lower = paths.get(i).toLowerCase(Locale.ROOT);
            for (int slash = lower.indexOf(SEPARATOR_CHAR); slash > 0;
                 slash = lower.indexOf(SEPARATOR_CHAR, slash + 1)) {
                Integer parent = files.get(lower.substring(0, slash));
                if (parent != null) {
                    throw new InvalidFileException(FILE_AS_FOLDER.formatted(FIELD.formatted(i), parent));
                }
            }
        }
    }

    private static InvalidFileException invalid(String template, String field) {
        return new InvalidFileException(template.formatted(field));
    }
}
