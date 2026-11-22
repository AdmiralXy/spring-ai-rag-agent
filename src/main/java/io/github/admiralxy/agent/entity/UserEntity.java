package io.github.admiralxy.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_users")
@Getter
@Setter
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "c_id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "c_email", nullable = false)
    private String email;

    @Column(name = "c_password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "c_upload_kdf_salt", nullable = false)
    private String uploadKdfSalt;

    @Column(name = "c_upload_key", columnDefinition = "bytea")
    private byte[] uploadKey;

    @CreationTimestamp
    @Column(name = "c_created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "c_updated_at", nullable = false)
    private Instant updatedAt;
}
