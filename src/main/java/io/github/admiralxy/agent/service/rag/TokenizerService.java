package io.github.admiralxy.agent.service.rag;

import java.util.List;

public interface TokenizerService {

    int countTokens(String text);

    String truncateToTokens(String text, int maxTokens);

    List<String> splitToTokenChunks(String text, int maxTokens);
}
