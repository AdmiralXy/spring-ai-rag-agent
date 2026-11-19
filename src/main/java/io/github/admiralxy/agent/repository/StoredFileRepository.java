package io.github.admiralxy.agent.repository;

import io.github.admiralxy.agent.domain.StoredFile;
import io.github.admiralxy.agent.entity.StoredFileEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFileEntity, UUID> {

    /**
     * Returns file metadata only; the binary content column is not selected.
     */
    @Query(
            value = """
                    select new io.github.admiralxy.agent.domain.StoredFile(
                        f.id, f.name, f.extension, f.contentType, f.size, f.createdAt
                    )
                    from StoredFileEntity f
                    """,
            countQuery = "select count(f) from StoredFileEntity f"
    )
    Page<StoredFile> findAllMetadata(Pageable pageable);

    /**
     * Deletes a file without loading its content into memory.
     *
     * @return number of deleted rows (0 or 1)
     */
    @Modifying
    @Query("delete from StoredFileEntity f where f.id = :id")
    int deleteByIdDirectly(@Param("id") UUID id);
}
