package io.github.admiralxy.agent.service.prompt;

import io.github.admiralxy.agent.domain.PromptTemplate;

import java.util.List;
import java.util.UUID;

public interface PromptTemplateService {

    List<PromptTemplate> getAll(UUID userId);

    PromptTemplate create(UUID userId, String name, String content);

    PromptTemplate update(UUID userId, UUID id, String name, String content);

    void delete(UUID userId, UUID id);
}
