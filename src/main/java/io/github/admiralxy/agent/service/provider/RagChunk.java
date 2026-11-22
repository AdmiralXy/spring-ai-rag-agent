package io.github.admiralxy.agent.service.provider;

import jakarta.annotation.Nullable;

public record RagChunk(String text, int number, int total, @Nullable String source, int part, int parts) {

    public RagChunk(String text, int number, int total) {
        this(text, number, total, null, number, total);
    }
}
