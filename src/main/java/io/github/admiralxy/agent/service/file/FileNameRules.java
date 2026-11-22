package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.exception.InvalidFileException;
import org.apache.commons.lang3.StringUtils;

import java.util.Locale;
import java.util.regex.Pattern;

public final class FileNameRules {

    public static final int MAX_NAME_LENGTH = 255;

    private static final Pattern EXTENSION_PATTERN = Pattern.compile("^[A-Za-z0-9]{1,20}$");
    private static final Pattern FORBIDDEN_NAME_CHARS = Pattern.compile("[/\\\\\\p{Cntrl}]");
    private static final String EXTENSION_DOT = ".";
    private static final String NAME_REQUIRED = "Field 'name' is required";
    private static final String NAME_TOO_LONG = "Field 'name' must be at most %d characters";
    private static final String NAME_FORBIDDEN_CHARS = "Field 'name' must not contain '/', '\\' or control characters";
    private static final String EXTENSION_REQUIRED = "Field 'extension' is required";
    private static final String EXTENSION_INVALID = "Field 'extension' must be 1-20 latin letters or digits";

    private FileNameRules() {
    }

    public static String normalizeName(String name) {
        if (StringUtils.isBlank(name)) {
            throw new InvalidFileException(NAME_REQUIRED);
        }
        String trimmed = name.strip();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new InvalidFileException(NAME_TOO_LONG.formatted(MAX_NAME_LENGTH));
        }
        if (FORBIDDEN_NAME_CHARS.matcher(trimmed).find()) {
            throw new InvalidFileException(NAME_FORBIDDEN_CHARS);
        }
        return trimmed;
    }

    public static String normalizeExtension(String extension) {
        if (StringUtils.isBlank(extension)) {
            throw new InvalidFileException(EXTENSION_REQUIRED);
        }
        String trimmed = StringUtils.removeStart(extension.strip(), EXTENSION_DOT);
        if (!EXTENSION_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidFileException(EXTENSION_INVALID);
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }
}
