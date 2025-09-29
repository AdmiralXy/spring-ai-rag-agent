package io.github.admiralxy.agent.service;

import java.util.List;

public interface TextChunkerService {

    /**
     * Universal text chunker.
     * - if text contains line breaks, chunk by lines (for code)
     * - else chunk by characters (for text without line breaks)
     *
     * @param text       source text
     * @param maxLines   maximum lines (for text with line breaks)
     * @param maxChars   maximum characters (for text without line breaks)
     * @param overlap    overlap between chunks
     * @return list of text chunks
     */
    List<String> chunk(String text, int maxLines, int maxChars, int overlap);
}
