package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.exception.InvalidFileException;

import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Parses base64 payloads sent by clients: plain base64 or a data URL ({@code data:<mime>;base64,<data>}).
 */
public final class Base64Content {

    private static final String DATA_URL_PREFIX = "data:";
    private static final String BASE64_MARKER = ";base64,";
    private static final Pattern WHITESPACE = Pattern.compile("\\s");

    private Base64Content() {
    }

    /**
     * Extracts the base64 payload, stripping a data URL prefix and whitespace.
     */
    public static String payload(String content) {
        if (content == null || content.isBlank()) {
            throw new InvalidFileException("Field 'content' is required");
        }
        String data = content;
        if (data.startsWith(DATA_URL_PREFIX)) {
            int markerIndex = data.indexOf(BASE64_MARKER);
            if (markerIndex < 0) {
                throw new InvalidFileException("Field 'content' is a data URL without base64 payload");
            }
            data = data.substring(markerIndex + BASE64_MARKER.length());
        }
        return WHITESPACE.matcher(data).replaceAll("");
    }

    /**
     * Upper bound of the decoded size; lets callers reject oversized payloads before decoding.
     */
    public static long estimateDecodedSize(String payload) {
        return (long) payload.length() * 3 / 4;
    }

    public static byte[] decode(String payload) {
        try {
            return Base64.getDecoder().decode(payload);
        } catch (IllegalArgumentException e) {
            throw new InvalidFileException("Field 'content' is not valid base64");
        }
    }
}
