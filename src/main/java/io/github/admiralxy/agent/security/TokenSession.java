package io.github.admiralxy.agent.security;

import java.time.Instant;
import java.util.UUID;

/**
 * A live sign-in token together with its owner and current expiry.
 */
public record TokenSession(UUID userId, String email, String tokenHash, Instant expiresAt) {

    public AuthUser user() {
        return new AuthUser(userId, email, tokenHash);
    }
}
