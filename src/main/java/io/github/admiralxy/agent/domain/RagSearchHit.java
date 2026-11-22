package io.github.admiralxy.agent.domain;

import jakarta.annotation.Nullable;
import org.apache.commons.lang3.math.NumberUtils;

import java.util.Map;

public record RagSearchHit(String id, String content, Map<String, Object> metadata) {

    public static final String SPACE = "space";
    public static final String DOC = "doc";
    public static final String CHUNK = "chunk";
    public static final String NUMBER = "number";
    public static final String TOTAL = "total";
    public static final String SOURCE = "source";
    public static final String PART = "part";
    public static final String PARTS = "parts";

    @Nullable
    public String space() {
        return string(SPACE);
    }

    @Nullable
    public String doc() {
        return string(DOC);
    }

    @Nullable
    public String source() {
        return string(SOURCE);
    }

    public int part() {
        String part = string(PART);
        return NumberUtils.toInt(part != null ? part : string(NUMBER), 0);
    }

    public boolean hasPart() {
        return metadata.containsKey(PART);
    }

    public RagSearchHit withContent(String newContent) {
        return new RagSearchHit(id, newContent, metadata);
    }

    @Nullable
    private String string(String key) {
        Object value = metadata.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
