package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.SummarizerModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SummarizerModelRepository extends JpaRepository<SummarizerModelEntity, UUID> {
}
