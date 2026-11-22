package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.ChatModelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChatModelRepository extends JpaRepository<ChatModelEntity, UUID> {

    @Query("select m from ChatModelEntity m where m.userId = :userId or m.builtin = true "
            + "order by m.priority asc, m.createdAt asc")
    List<ChatModelEntity> findAvailable(@Param("userId") UUID userId);

    @Query("select m from ChatModelEntity m where m.id = :id and (m.userId = :userId or m.builtin = true)")
    Optional<ChatModelEntity> findAvailable(@Param("id") UUID id, @Param("userId") UUID userId);
}
