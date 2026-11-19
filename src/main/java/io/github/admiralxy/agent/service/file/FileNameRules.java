package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.exception.InvalidFileException;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Validation rules for stored file names and extensions.
 */
public final class FileNameRules {

    public static final int MAX_NAME_LENGTH = 255;

    private static final Pattern EXTENSION_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,20}$");
    private static final Pattern FORBIDDEN_NAME_CHARS = Pattern.compile("[/\\\\\\p{Cntrl}]");

    private FileNameRules() {
    }

    public static String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidFileException("Field 'name' is required");
        }
        String trimmed = name.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidFileException("Field 'name' must be at most " + MAX_NAME_LENGTH + " characters");
        }
        if (FORBIDDEN_NAME_CHARS.matcher(trimmed).find()) {
            throw new InvalidFileException("Field 'name' must not contain '/', '\\' or control characters");
        }
        return trimmed;
    }

    public static String normalizeExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            throw new InvalidFileException("Field 'extension' is required");
        }
        String trimmed = extension.strip();
        if (trimmed.startsWith(".")) {
            trimmed = trimmed.substring(1);
        }
        if (!EXTENSION_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidFileException("Field 'extension' must be 1-20 latin letters or digits");
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
