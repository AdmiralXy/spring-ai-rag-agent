package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface EmbeddingModelRepository extends JpaRepository<EmbeddingModelEntity, UUID> {

    @Query("select distinct e.dimensions from EmbeddingModelEntity e")
    List<Integer> findDistinctDimensions();
}
