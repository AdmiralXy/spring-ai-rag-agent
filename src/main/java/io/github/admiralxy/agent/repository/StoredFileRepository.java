package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.entity.StoredFileEntity;
import io.github.admiralxy.agent.entity.StoredFileStorageRef;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFileEntity, UUID> {

    @Query(
            value = """
                    select new io.github.admiralxy.agent.domain.StoredFile(
                        f.id, f.name, f.extension, f.contentType, f.size, f.createdAt
                    )
                    from StoredFileEntity f
                    where f.userId = :userId
                    """,
            countQuery = "select count(f) from StoredFileEntity f where f.userId = :userId"
    )
    Page<StoredFile> findAllMetadata(@Param("userId") UUID userId, Pageable pageable);

    Optional<StoredFileEntity> findByIdAndUserId(UUID id, UUID userId);

    @Query("select new io.github.admiralxy.agent.entity.StoredFileStorageRef(f.id, f.storagePath) "
            + "from StoredFileEntity f where f.id = :id and f.userId = :userId")
    Optional<StoredFileStorageRef> findStorageRef(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query("delete from StoredFileEntity f where f.id = :id and f.userId = :userId")
    int deleteByIdDirectly(@Param("id") UUID id, @Param("userId") UUID userId);
}
