package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.EmbeddingsModelSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmbeddingsModelSettingsRepository extends JpaRepository<EmbeddingsModelSettingsEntity, Short> {
}
