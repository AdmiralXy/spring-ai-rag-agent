package io.github.admiralxy.agent.service;

public interface TokenizerService {

    /**
     * Counts the number of tokens in the given text using the CL100K_BASE encoding.
     *
     * @param text input string
     * @return number of tokens
     */
    int countTokens(String text);

    /**
     * Truncates the given text so that it contains at most the specified number of tokens.
     *
     * @param text input string
     * @param maxTokens maximum allowed number of tokens
     * @return truncated text
     */
    String truncateToTokens(String text, int maxTokens);
}
