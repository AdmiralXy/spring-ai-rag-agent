package io.github.admiralxy.agent.controller.response.prompt;

import io.github.admiralxy.agent.domain.PromptTemplate;

import java.util.List;

public record GetPromptTemplatesRs(List<PromptTemplate> templates) {
}
