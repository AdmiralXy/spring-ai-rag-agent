package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.service.TextChunkerService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class TextChunkerServiceImpl implements TextChunkerService {

    private static final Pattern JAVA_BOUNDARY = Pattern.compile(
            "(?m)^(?=\\s*(?:public|protected|private)?\\s*(?:class|interface|enum)\\b)|"
                    + "(?m)^(?=\\s*(?:public|protected|private|static|final|synchronized|abstract)\\s+[^;{=]+\\{)"
    );

    @Override
    public List<String> chunk(String text, int maxLines, int maxChars, int overlap) {
        if (StringUtils.isBlank(text)) {
            return List.of();
        }
        String normalized = normalizeNewlines(text);
        if (looksLikeCode(normalized)) {
            return chunkByCodeBlocks(normalized, maxLines, maxChars, overlap);
        }
        if (normalized.contains("\n")) {
            return chunkByParagraphs(normalized, maxChars, overlap);
        }
        return chunkByChars(normalized, maxChars, overlap);
    }

    private static String normalizeNewlines(String s) {
        return s.replace("\r\n", "\n").replace("\r", "\n");
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
            String part = paragraph.strip();
            if (part.isEmpty()) {
                continue;
            }
            if (!current.isEmpty()) {
                if (current.length() + 2 + part.length() > maxChars) {
                    addChunkWithWordOverlap(chunks, current.toString(), overlapWords, maxChars);
                    current.setLength(0);
                } else {
                    current.append("\n\n");
                }
            }
            if (part.length() > maxChars) {
                if (!current.isEmpty()) {
                    addChunkWithWordOverlap(chunks, current.toString(), overlapWords, maxChars);
                    current.setLength(0);
                }
                for (String wchunk : splitByWords(part, maxChars)) {
                    addChunkWithWordOverlap(chunks, wchunk, overlapWords, maxChars);
                }
            } else {
                current.append(part);
            }
        }
        if (!current.isEmpty()) {
            addChunkWithWordOverlap(chunks, current.toString(), overlapWords, maxChars);
        }
        return chunks;
    }

    private static List<String> chunkByCodeBlocks(String text, int maxLines, int maxChars, int overlapLines) {
        String[] blocks = JAVA_BOUNDARY.split(text);
        List<String> result = new ArrayList<>();
        StringBuilder acc = new StringBuilder();
        int accLines = 0;
        for (String block : blocks) {
            String b = block.strip();
            if (b.isEmpty()) {
                continue;
            }
            if (b.length() > maxChars || countLines(b) > maxLines) {
                if (!acc.isEmpty()) {
                    addChunkWithLineOverlap(result, acc.toString(), overlapLines, maxChars, maxLines);
                    acc.setLength(0);
                    accLines = 0;
                }
                for (String part : splitByLinesWithLimit(b, maxLines, maxChars)) {
                    addChunkWithLineOverlap(result, part, overlapLines, maxChars, maxLines);
                }
                continue;
            }
            boolean needSep = !acc.isEmpty();
            int newLen = acc.length() + (needSep ? 1 : 0) + b.length();
            int newLines = accLines + (needSep ? 1 : 0) + countLines(b);
            if (newLen > maxChars || newLines > maxLines) {
                addChunkWithLineOverlap(result, acc.toString(), overlapLines, maxChars, maxLines);
                acc.setLength(0);
                accLines = 0;
            } else if (needSep) {
                acc.append('\n');
                accLines += 1;
            }
            if (b.length() > maxChars || countLines(b) > maxLines) {
                for (String part : splitByLinesWithLimit(b, maxLines, maxChars)) {
                    addChunkWithLineOverlap(result, part, overlapLines, maxChars, maxLines);
                }
            } else {
                acc.append(b);
                accLines += countLines(b);
            }
        }
        if (!acc.isEmpty()) {
            addChunkWithLineOverlap(result, acc.toString(), overlapLines, maxChars, maxLines);
        }
        return result;
    }

    private static int countLines(String s) {
        if (s.isEmpty()) {
            return 0;
        }
        int n = 1;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    private static List<String> chunkByChars(String text, int maxChars, int overlapWords) {
        List<String> chunks = new ArrayList<>();
        String[] words = text.trim().split("\\s+");
        int start = 0;
        while (start < words.length) {
            StringBuilder chunk = new StringBuilder();
            int i = start;
            while (i < words.length) {
                String word = words[i];
                int add = chunk.isEmpty() ? word.length() : word.length() + 1;
                if (chunk.length() + add > maxChars) {
                    break;
                }
                if (!chunk.isEmpty()) {
                    chunk.append(' ');
                }
                chunk.append(word);
                i++;
            }
            if (!chunk.isEmpty()) {
                addChunkWithWordOverlap(chunks, chunk.toString(), overlapWords, maxChars);
            }
            if (i == start) {
                i++;
            }
            start = Math.max(i - overlapWords, i);
        }
        return chunks;
    }

    private static List<String> splitByLinesWithLimit(String text, int maxLines, int maxChars) {
        String[] lines = text.split("\\R", -1);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int linesCount = 0;
        for (String line : lines) {
            String l = line;
            int addChars = (current.isEmpty() ? l.length() : l.length() + 1);
            int addLines = 1;
            if (current.length() + addChars > maxChars || linesCount + addLines > maxLines) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                    linesCount = 0;
                }
            }
            if (l.length() > maxChars) {
                if (!current.isEmpty()) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                    linesCount = 0;
                }
                chunks.addAll(splitByWords(l, maxChars));
                continue;
            }
            if (!current.isEmpty()) {
                current.append('\n');
                linesCount++;
            }
            current.append(l);
        }
        if (!current.isEmpty()) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private static void addChunkWithLineOverlap(List<String> chunks, String chunk, int overlapLines, int maxChars, int maxLines) {
        if (StringUtils.isBlank(chunk)) {
            return;
        }
        String c = chunk.trim();
        if (c.length() > maxChars || countLines(c) > maxLines) {
            for (String part : splitByLinesWithLimit(c, maxLines, maxChars)) {
                addChunkWithLineOverlap(chunks, part, overlapLines, maxChars, maxLines);
            }
            return;
        }
        if (!chunks.isEmpty() && overlapLines > 0) {
            String prev = chunks.getLast();
            String[] prevLines = prev.split("\\R");
            int count = Math.min(overlapLines, prevLines.length);
            StringBuilder prefix = new StringBuilder();
            for (int i = prevLines.length - count; i < prevLines.length; i++) {
                if (!prefix.isEmpty()) {
                    prefix.append('\n');
                }
                prefix.append(prevLines[i]);
            }
            String merged = prefix.isEmpty() ? c : (prefix + "\n" + c);
            while ((merged.length() > maxChars || countLines(merged) > maxLines) && !prefix.isEmpty()) {
                int idx = prefix.indexOf("\n");
                if (idx < 0) {
                    prefix.setLength(0);
                } else {
                    prefix.delete(0, idx + 1);
                }
                merged = prefix.isEmpty() ? c : (prefix + "\n" + c);
            }
            c = merged.length() > maxChars ? c.substring(0, Math.min(c.length(), maxChars)) : merged;
        }
        chunks.add(c.trim());
    }

    private static void addChunkWithWordOverlap(List<String> chunks, String chunk, int overlapWords, int maxChars) {
        if (StringUtils.isBlank(chunk)) {
            return;
        }
        String c = chunk.trim();
        if (c.length() > maxChars) {
            for (String part : splitByWords(c, maxChars)) {
                addChunkWithWordOverlap(chunks, part, overlapWords, maxChars);
            }
            return;
        }
        if (!chunks.isEmpty() && overlapWords > 0) {
            String prev = chunks.get(chunks.size() - 1);
            String[] words = prev.trim().split("\\s+");
            int count = Math.min(overlapWords, words.length);
            StringBuilder prefix = new StringBuilder();
            for (int i = words.length - count; i < words.length; i++) {
                if (!prefix.isEmpty()) {
                    prefix.append(' ');
                }
                prefix.append(words[i]);
            }
            String merged = prefix.isEmpty() ? c : (prefix + " " + c);
            while (merged.length() > maxChars) {
                int spaceIdx = prefix.indexOf(" ");
                if (spaceIdx < 0) {
                    prefix.setLength(0);
                } else {
                    prefix.delete(0, spaceIdx + 1);
                }
                merged = prefix.isEmpty() ? c : (prefix + " " + c);
            }
            c = merged.length() > maxChars ? c : merged;
        }
        chunks.add(c.trim());
    }

    private static List<String> splitByWords(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        String[] words = text.trim().split("\\s+");
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
            int add = current.isEmpty() ? word.length() : word.length() + 1;
            if (current.length() + add > maxChars) {
                result.add(current.toString().trim());
                current.setLength(0);
            }
            if (!current.isEmpty()) {
                current.append(' ');
            }
            current.append(word);
        }
        if (!current.isEmpty()) {
            result.add(current.toString().trim());
        }
        return result;
    }
}
