package io.github.admiralxy.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "t_files")
@Getter
@Setter
public class StoredFileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "c_id", updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "c_user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "c_name", nullable = false)
    private String name;

    @Column(name = "c_extension", nullable = false)
    private String extension;

    @Column(name = "c_content_type", nullable = false)
    private String contentType;

    @Column(name = "c_size", nullable = false)
    private long size;

    @Column(name = "c_content", columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "c_storage_path")
    private String storagePath;

    @Column(name = "c_created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }
}
