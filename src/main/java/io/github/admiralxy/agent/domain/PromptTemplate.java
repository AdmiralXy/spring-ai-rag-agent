package io.github.admiralxy.agent.domain;

import java.util.UUID;

public record PromptTemplate(UUID id, String name, String content) {
}
