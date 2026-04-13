package io.github.admiralxy.agent.service.impl;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkerServiceImplTest {

    private final TextChunkerServiceImpl chunker = new TextChunkerServiceImpl();

    @Test
    void chunk_shouldSplitCodeByMaxLines() {
        String text = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> "int value" + i + " = " + i + ";")
                .collect(Collectors.joining("\n"));

        List<String> chunks = chunker.chunk(text, 100, 50_000, 5);

        assertEquals(2, chunks.size());
        assertTrue(countLines(chunks.get(0)) <= 100);
        assertTrue(countLines(chunks.get(1)) <= 100);
    }

    @Test
    void chunk_shouldFinishWithoutStackOverflowOnLargeInput() {
        String text = IntStream.rangeClosed(1, 350)
                .mapToObj(i -> "int value" + i + " = " + i + ";")
                .collect(Collectors.joining("\n"));

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> {
            List<String> chunks = chunker.chunk(text, 100, 50_000, 5);
            assertTrue(chunks.size() >= 4);
        });
    }

    private int countLines(String s) {
        if (s == null || s.isEmpty()) {
            return 0;
        }
        int count = 1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }
}
