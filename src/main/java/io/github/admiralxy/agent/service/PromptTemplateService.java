package io.github.admiralxy.agent.service;

import io.github.admiralxy.agent.domain.PromptTemplate;

import java.util.List;
import java.util.UUID;

public interface PromptTemplateService {

    List<PromptTemplate> getAll();

    PromptTemplate create(String name, String content);

    PromptTemplate update(UUID id, String name, String content);

    void delete(UUID id);
}
