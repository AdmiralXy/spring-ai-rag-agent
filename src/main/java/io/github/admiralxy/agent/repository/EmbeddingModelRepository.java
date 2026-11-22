package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmbeddingModelRepository extends JpaRepository<EmbeddingModelEntity, Short> {
}
