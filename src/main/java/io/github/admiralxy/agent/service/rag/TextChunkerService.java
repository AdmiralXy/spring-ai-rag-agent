package io.github.admiralxy.agent.service.rag;

import java.util.List;

public interface TextChunkerService {

    List<String> chunk(String text, int maxLines, int maxChars, int overlap);

    List<String> chunk(String text, int maxLines, int maxChars, int overlapLines, int overlapWords);
}
