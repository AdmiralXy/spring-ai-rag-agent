package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.exception.InvalidFileException;
import org.apache.commons.lang3.StringUtils;

import java.util.Base64;
import java.util.regex.Pattern;

public final class Base64Content {

    private static final String DATA_URL_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";
    private static final String CONTENT_FIELD = "content";
    private static final String FIELD_REQUIRED = "Field '%s' is required";
    private static final String FIELD_NOT_BASE64 = "Field '%s' is not valid base64";
    private static final String DATA_URL_WITHOUT_BASE64 = "Field 'content' is a data URL without base64 payload";
    private static final Pattern WHITESPACE = Pattern.compile("\\s");
    private static final int BASE64_GROUP = 4;
    private static final int BYTES_PER_GROUP = 3;

    private Base64Content() {
    }

    public static String payload(String content) {
        if (StringUtils.isBlank(content)) {
            throw new InvalidFileException(FIELD_REQUIRED.formatted(CONTENT_FIELD));
        }
        String data = content;
        if (data.startsWith(DATA_URL_PREFIX)) {
            int markerIndex = data.indexOf(BASE64_MARKER);
            if (markerIndex < 0) {
                throw new InvalidFileException(DATA_URL_WITHOUT_BASE64);
            }
            data = data.substring(markerIndex + BASE64_MARKER.length());
        }
        return WHITESPACE.matcher(data).replaceAll(StringUtils.EMPTY);
    }

    public static long estimateDecodedSize(String payload) {
        return (long) payload.length() * BYTES_PER_GROUP / BASE64_GROUP;
    }

    public static byte[] decode(String payload) {
        return decodeOrFail(payload, CONTENT_FIELD);
    }

    public static byte[] decodeField(String value, String field) {
        if (StringUtils.isEmpty(value)) {
            throw new InvalidFileException(FIELD_REQUIRED.formatted(field));
        }
        return decodeOrFail(value, field);
    }

    private static byte[] decodeOrFail(String value, String field) {
        try {
            return Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidFileException(FIELD_NOT_BASE64.formatted(field));
        }
    }
}
