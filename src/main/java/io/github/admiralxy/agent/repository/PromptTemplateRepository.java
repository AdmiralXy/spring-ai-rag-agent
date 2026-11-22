package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.PromptTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromptTemplateRepository extends JpaRepository<PromptTemplateEntity, UUID> {

    List<PromptTemplateEntity> findByUserIdOrderByCreatedAtAsc(UUID userId);

    Optional<PromptTemplateEntity> findByIdAndUserId(UUID id, UUID userId);
}
