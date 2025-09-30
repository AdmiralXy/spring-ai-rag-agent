package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.service.TextChunkerService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TextChunkerServiceImpl implements TextChunkerService {

    @Override
    public List<String> chunk(String text, int maxLines, int maxChars, int overlap) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }

        if (looksLikeCode(text)) {
            return chunkByCodeBlocks(text, maxChars, overlap);
        }
        if (text.contains(StringUtils.LF)) {
            return chunkByParagraphs(text, maxChars, overlap);
        }
        return chunkByChars(text, maxChars, overlap);
    }

    private static boolean looksLikeCode(String text) {
        long semicolons = text.chars().filter(c -> c == ';').count();
        long braces = text.chars().filter(c -> c == '{' || c == '}').count();
        return semicolons + braces > 3;
    }

    private static List<String> chunkByParagraphs(String text, int maxChars, int overlapWords) {
        String[] paragraphs = text.split("\\R{2,}");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() + 2 > maxChars) {
                if (!current.isEmpty()) {
                    addChunkWithWordOverlap(chunks, current.toString(), overlapWords, maxChars);
                    current.setLength(0);
                }
            }
            current.append(paragraph).append("\n\n");
        }

        if (!current.isEmpty()) {
            addChunkWithWordOverlap(chunks, current.toString(), overlapWords, maxChars);
        }
        return chunks;
    }

    private static List<String> chunkByCodeBlocks(String text, int maxChars, int overlapLines) {
        String[] blocks = text.split("(?=\\b(public|private|protected|class|interface)\\b)");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String block : blocks) {
            if (StringUtils.isBlank(block)) {
                continue;
            }

            if (block.length() > maxChars) {
                for (String part : splitByLines(block, maxChars)) {
                    addChunkWithLineOverlap(chunks, part, overlapLines, maxChars);
                }
                continue;
            }

            if (current.length() + block.length() > maxChars) {
                addChunkWithLineOverlap(chunks, current.toString(), overlapLines, maxChars);
                current.setLength(0);
            }
            current.append(block).append(StringUtils.LF);
        }

        if (!current.isEmpty()) {
            addChunkWithLineOverlap(chunks, current.toString(), overlapLines, maxChars);
        }
        return chunks;
    }

    private static List<String> chunkByChars(String text, int maxChars, int overlapWords) {
        List<String> chunks = new ArrayList<>();
        String[] words = text.split("\\s+");

        int start = 0;
        while (start < words.length) {
            StringBuilder chunk = new StringBuilder();
            int i = start;
            while (i < words.length) {
                String word = words[i];
                if (chunk.length() + word.length() + 1 > maxChars) {
                    break;
                }
                chunk.append(word).append(StringUtils.SPACE);
                i++;
            }
            if (!chunk.isEmpty()) {
                chunks.add(chunk.toString().trim());
            }
            start = Math.max(i - overlapWords, i);
            if (start >= words.length) {
                break;
            }
        }
        return chunks;
    }

    private static List<String> splitByLines(String text, int maxChars) {
        String[] lines = text.split("\\R");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            if (current.length() + line.length() + 1 > maxChars) {
                chunks.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(line).append(StringUtils.LF);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private static void addChunkWithLineOverlap(List<String> chunks, String chunk, int overlapLines, int maxChars) {
        if (StringUtils.isBlank(chunk)) {
            return;
        }
        if (chunk.length() > maxChars) {
            chunks.addAll(splitByLines(chunk, maxChars));
            return;
        }

        if (!chunks.isEmpty() && overlapLines > 0) {
            String prev = chunks.getLast();
            String[] prevLines = prev.split("\\R");
            int count = Math.min(overlapLines, prevLines.length);

            StringBuilder prefix = new StringBuilder();
            for (int i = prevLines.length - count; i < prevLines.length; i++) {
                prefix.append(prevLines[i]).append(StringUtils.LF);
            }
            chunk = (prefix + chunk).trim();
        }
        chunks.add(chunk.trim());
    }

    private static void addChunkWithWordOverlap(List<String> chunks, String chunk, int overlapWords, int maxChars) {
        if (StringUtils.isBlank(chunk)) {
            return;
        }
        if (chunk.length() > maxChars) {
            chunks.addAll(splitByWords(chunk, maxChars));
            return;
        }

        if (!chunks.isEmpty() && overlapWords > 0) {
            String prev = chunks.getLast();
            String[] words = prev.split("\\s+");
            int count = Math.min(overlapWords, words.length);

            StringBuilder prefix = new StringBuilder();
            for (int i = words.length - count; i < words.length; i++) {
                prefix.append(words[i]).append(StringUtils.SPACE);
            }
            chunk = (prefix + chunk).trim();
        }
        chunks.add(chunk.trim());
    }

    private static List<String> splitByWords(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (word.length() > maxChars) {
                if (!current.isEmpty()) {
                    result.add(current.toString().trim());
                    current.setLength(0);
                }
                int start = 0;
                while (start < word.length()) {
                    int end = Math.min(start + maxChars, word.length());
                    result.add(word.substring(start, end));
                    start = end;
                }
                continue;
            }

            if (current.length() + word.length() + 1 > maxChars) {
                result.add(current.toString().trim());
                current.setLength(0);
            }
            current.append(word).append(StringUtils.SPACE);
        }
        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }
        return result;
    }
}
