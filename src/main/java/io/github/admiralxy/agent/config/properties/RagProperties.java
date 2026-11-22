package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.chat.rag")
@Getter
@Setter
public class RagProperties {

    private static final int DEFAULT_PERCENTAGE = 30;
    private static final int DEFAULT_MAX_TOKENS = 24000;
    private static final int DEFAULT_TOP_K = 40;
    private static final double DEFAULT_KEYWORD_WEIGHT = 0.6;
    private static final int DEFAULT_EXPAND_TOP_HITS = 3;

    /**
     * Share of the model context window (percent) for knowledge-base excerpts, capped by {@code max-tokens}.
     */
    private int percentage = DEFAULT_PERCENTAGE;

    /**
     * Hard cap of knowledge-base excerpts in tokens, whatever the context window.
     */
    private int maxTokens = DEFAULT_MAX_TOKENS;

    /**
     * Candidates of each search (vector and full-text) before they are fused.
     */
    private int topK = DEFAULT_TOP_K;

    /**
     * Weight of the full-text ranking in the fusion; the vector ranking has weight 1.
     */
    private double keywordWeight = DEFAULT_KEYWORD_WEIGHT;

    /**
     * Best hits that get their neighbouring chunks (previous and next part of the same source) added.
     */
    private int expandTopHits = DEFAULT_EXPAND_TOP_HITS;

    /**
     * Splitting of documents into chunks for indexing.
     */
    private Chunking chunking = new Chunking();

    @Getter
    @Setter
    public static class Chunking {

        private static final int DEFAULT_MAX_CHARS = 2000;
        private static final int DEFAULT_MAX_LINES = 60;
        private static final int DEFAULT_OVERLAP_LINES = 5;
        private static final int DEFAULT_OVERLAP_WORDS = 40;

        /**
         * Max characters of a chunk.
         */
        private int maxChars = DEFAULT_MAX_CHARS;

        /**
         * Max lines of a code chunk.
         */
        private int maxLines = DEFAULT_MAX_LINES;

        /**
         * Lines repeated from the previous chunk of code.
         */
        private int overlapLines = DEFAULT_OVERLAP_LINES;

        /**
         * Words repeated from the previous chunk of text.
         */
        private int overlapWords = DEFAULT_OVERLAP_WORDS;
    }
}
