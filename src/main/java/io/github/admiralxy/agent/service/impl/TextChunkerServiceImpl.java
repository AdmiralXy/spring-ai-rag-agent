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

    private static List<String> chunkByParagraphs(String text, int maxChars, int overlap) {
        String[] paragraphs = text.split("\\R{2,}");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : paragraphs) {
            if (current.length() + paragraph.length() + 2 > maxChars) {
                if (current.length() > 0) {
                    addChunkWithOverlap(chunks, current.toString(), overlap, maxChars);
                    current.setLength(0);
                }
            }
            current.append(paragraph).append("\n\n");
        }

        if (current.length() > 0) {
            addChunkWithOverlap(chunks, current.toString(), overlap, maxChars);
        }
        return chunks;
    }

    private static List<String> chunkByCodeBlocks(String text, int maxChars, int overlap) {
        String[] blocks = text.split("(?=\\b(public|private|protected|class|interface)\\b)");
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String block : blocks) {
            if (StringUtils.isBlank(block)) continue;

            if (current.length() + block.length() > maxChars) {
                if (current.length() > 0) {
                    addChunkWithOverlap(chunks, current.toString(), overlap, maxChars);
                    current.setLength(0);
                }
            }
            current.append(block).append("\n");
        }

        if (current.length() > 0) {
            addChunkWithOverlap(chunks, current.toString(), overlap, maxChars);
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
                if (chunk.length() + word.length() + 1 > maxChars) break;
                chunk.append(word).append(" ");
                i++;
            }
            if (chunk.length() > 0) {
                chunks.add(chunk.toString().trim());
            }
            // следующий чанк начинается на overlapWords слов раньше конца
            start = Math.max(i - overlapWords, i);
            if (start == i && i >= words.length) break;
        }
        return chunks;
    }


    private static List<String> splitByMaxChars(String text, int maxChars) {
        List<String> result = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            // если текущее слово само длиннее maxChars — режем жёстко
            if (word.length() > maxChars) {
                if (current.length() > 0) {
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

            // если слово не помещается в текущий чанк → закрываем чанк
            if (current.length() + word.length() + 1 > maxChars) {
                result.add(current.toString().trim());
                current.setLength(0);
            }

            current.append(word).append(' ');
        }

        if (current.length() > 0) {
            result.add(current.toString().trim());
        }

        return result;
    }

    private static void addChunkWithOverlap(List<String> chunks, String chunk, int overlap, int maxChars) {
        if (StringUtils.isBlank(chunk)) return;

        // если слишком длинный — режем по словам
        if (chunk.length() > maxChars) {
            chunks.addAll(splitByMaxChars(chunk, maxChars));
            return;
        }

        if (!chunks.isEmpty() && overlap > 0) {
            String prev = chunks.get(chunks.size() - 1);
            String[] words = prev.split("\\s+");
            int overlapWords = Math.min(overlap, words.length);

            // берём последние overlap слов, а не символы
            StringBuilder prefix = new StringBuilder();
            for (int i = words.length - overlapWords; i < words.length; i++) {
                prefix.append(words[i]).append(" ");
            }

            // склеиваем overlap + новый чанк
            chunk = (prefix + chunk).trim();
        }

        chunks.add(chunk.trim());
    }
}
