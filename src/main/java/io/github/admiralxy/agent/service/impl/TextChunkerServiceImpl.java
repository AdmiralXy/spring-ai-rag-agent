package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.service.TextChunkerService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class TextChunkerServiceImpl implements TextChunkerService {

    @Override
    public List<String> chunk(String text, int maxLines, int maxChars, int overlap) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        if (text.contains(StringUtils.LF)) {
            return chunkByLinesSafe(text, maxLines, overlap, maxChars);
        }
        return chunkByChars(text, maxChars, overlap);
    }

    private static List<String> chunkByLinesSafe(String text, int maxLines, int overlap, int maxChars) {
        List<String> lines = Arrays.asList(text.split(StringUtils.LF));
        List<String> chunks = new ArrayList<>();

        for (int i = 0; i < lines.size(); i += (maxLines - overlap)) {
            int end = Math.min(i + maxLines, lines.size());
            List<String> subList = lines.subList(i, end);
            String chunk = String.join(StringUtils.LF, subList);

            if (chunk.length() > maxChars) {
                chunks.addAll(splitByMaxChars(chunk, maxChars));
            } else {
                chunks.add(chunk);
            }
        }
        return chunks;
    }

    private static List<String> chunkByChars(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            chunks.add(text.substring(start, end));
            start = end - overlap;
            if (start < 0) {
                start = 0;
            }
        }
        return chunks;
    }

    private static List<String> splitByMaxChars(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + maxChars, text.length());

            if (end < text.length()) {
                int lastSpace = StringUtils.lastIndexOf(text, StringUtils.SPACE, end);
                if (lastSpace >= start) {
                    end = lastSpace;
                }
            }

            result.add(StringUtils.trim(text.substring(start, end)));
            start = end;
        }
        return result;
    }
}
