package io.github.admiralxy.agent.controller.response.prompt;

import java.util.UUID;

public record CreatePromptTemplateRs(UUID id, String name, String content) {
}
