package io.github.admiralxy.agent.security;

import java.util.UUID;

/**
 * The signed-in user, available in controllers as {@code @AuthenticationPrincipal AuthUser user}.
 */
public record AuthUser(UUID id, String email, String tokenHash) {
}
