package io.github.admiralxy.agent.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;

public enum ChatPartType {
    TEXT,
    THINKING,
    TOOL,
    ERROR,
    NOTICE;

    @JsonValue
    public String value() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ChatPartType of(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(value));
    }
}
