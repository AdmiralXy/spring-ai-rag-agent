package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.PromptTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplateEntity, UUID> {
}
