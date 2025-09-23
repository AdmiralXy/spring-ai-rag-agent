package io.github.admiralxy.agent.domain;

import java.util.Map;

public record RagDocument(String id, String content, Map<String, Object> metadata) {
}
