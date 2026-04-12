package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.domain.PromptTemplate;
import io.github.admiralxy.agent.entity.PromptTemplateEntity;
import io.github.admiralxy.agent.repository.PromptTemplateRepository;
import io.github.admiralxy.agent.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private static final String TEMPLATE_NOT_FOUND = "Prompt template not found";

    private final PromptTemplateRepository promptTemplateRepository;

    @Override
    public List<PromptTemplate> getAll() {
        return promptTemplateRepository.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public PromptTemplate create(String name, String content) {
        PromptTemplateEntity template = new PromptTemplateEntity();
        template.setName(name);
        template.setContent(content);
        return toDomain(promptTemplateRepository.save(template));
    }

    @Override
    public PromptTemplate update(UUID id, String name, String content) {
        PromptTemplateEntity template = promptTemplateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException(TEMPLATE_NOT_FOUND));
        template.setName(name);
        template.setContent(content);
        return toDomain(promptTemplateRepository.save(template));
    }

    @Override
    public void delete(UUID id) {
        if (!promptTemplateRepository.existsById(id)) {
            throw new IllegalArgumentException(TEMPLATE_NOT_FOUND);
        }
        promptTemplateRepository.deleteById(id);
    }

    private PromptTemplate toDomain(PromptTemplateEntity template) {
        return new PromptTemplate(template.getId(), template.getName(), template.getContent());
    }
}
