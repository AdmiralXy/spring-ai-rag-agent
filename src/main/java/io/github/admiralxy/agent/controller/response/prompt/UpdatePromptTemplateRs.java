package io.github.admiralxy.agent.controller.response.prompt;

import java.util.UUID;

public record UpdatePromptTemplateRs(UUID id, String name, String content) {
}
