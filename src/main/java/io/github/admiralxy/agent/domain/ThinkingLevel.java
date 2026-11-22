package io.github.admiralxy.agent.domain;

import jakarta.annotation.Nullable;

import java.util.Locale;

public enum ThinkingLevel {
    OFF,
    LOW,
    MEDIUM,
    HIGH;

    @Nullable
    public static ThinkingLevel parse(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String effort() {
        return name().toLowerCase(Locale.ROOT);
    }
}
