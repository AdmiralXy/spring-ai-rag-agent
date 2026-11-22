package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.entity.ChatEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ChatRepository extends JpaRepository<ChatEntity, UUID> {

    Page<ChatEntity> findByUserId(UUID userId, Pageable pageable);

    Page<ChatEntity> findByUserIdAndTitleContainingIgnoreCase(UUID userId, String title, Pageable pageable);

    Optional<ChatEntity> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByIdAndUserId(UUID id, UUID userId);

    @Modifying
    @Transactional
    @Query("update ChatEntity c set c.updatedAt = :now where c.id = :id")
    int touch(@Param("id") UUID id, @Param("now") Instant now);
}
