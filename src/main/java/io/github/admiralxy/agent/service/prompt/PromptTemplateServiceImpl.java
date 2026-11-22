package io.github.admiralxy.agent.service.prompt;

import io.github.admiralxy.agent.domain.PromptTemplate;
import io.github.admiralxy.agent.entity.PromptTemplateEntity;
import io.github.admiralxy.agent.exception.ApiException;
import io.github.admiralxy.agent.repository.PromptTemplateRepository;
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
    public List<PromptTemplate> getAll(UUID userId) {
        return promptTemplateRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public PromptTemplate create(UUID userId, String name, String content) {
        PromptTemplateEntity template = new PromptTemplateEntity();
        template.setUserId(userId);
        template.setName(name);
        template.setContent(content);
        return toDomain(promptTemplateRepository.save(template));
    }

    @Override
    public PromptTemplate update(UUID userId, UUID id, String name, String content) {
        PromptTemplateEntity template = find(userId, id);
        template.setName(name);
        template.setContent(content);
        return toDomain(promptTemplateRepository.save(template));
    }

    @Override
    public void delete(UUID userId, UUID id) {
        promptTemplateRepository.delete(find(userId, id));
    }

    private PromptTemplateEntity find(UUID userId, UUID id) {
        return promptTemplateRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound(TEMPLATE_NOT_FOUND));
    }

    private PromptTemplate toDomain(PromptTemplateEntity template) {
        return new PromptTemplate(template.getId(), template.getName(), template.getContent());
    }
}
