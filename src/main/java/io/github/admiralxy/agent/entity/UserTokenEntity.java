package io.github.admiralxy.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "t_user_tokens")
@Getter
@Setter
public class UserTokenEntity {

    /**
     * Hex SHA-256 of the token.
     */
    @Id
    @Column(name = "c_token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "c_user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "c_created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "c_expires_at", nullable = false)
    private Instant expiresAt;
}
