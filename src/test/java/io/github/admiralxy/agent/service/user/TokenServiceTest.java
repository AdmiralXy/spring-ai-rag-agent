package io.github.admiralxy.agent.service.user;

import io.github.admiralxy.agent.config.properties.SecurityProperties;
import io.github.admiralxy.agent.entity.UserTokenEntity;
import io.github.admiralxy.agent.repository.UserTokenRepository;
import io.github.admiralxy.agent.security.AuthUser;
import io.github.admiralxy.agent.security.TokenSession;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-22T10:00:00Z");

    private final UserTokenRepository repository = mock(UserTokenRepository.class);
    private final SecurityProperties properties = new SecurityProperties();
    private final TokenService service = new TokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void storesOnlyTheHashOfTheIssuedToken() {
        UUID userId = UUID.randomUUID();

        String token = service.issue(userId);

        ArgumentCaptor<UserTokenEntity> saved = ArgumentCaptor.forClass(UserTokenEntity.class);
        verify(repository).save(saved.capture());
        assertTrue(token.length() >= 40);
        assertEquals(userId, saved.getValue().getUserId());
        assertEquals(TokenService.hash(token), saved.getValue().getTokenHash());
        assertEquals(64, saved.getValue().getTokenHash().length());
        assertFalse(saved.getValue().getTokenHash().contains(token));
        assertEquals(NOW.plus(Duration.ofDays(90)), saved.getValue().getExpiresAt());
    }

    @Test
    void ignoresBlankOrOversizedTokens() {
        assertTrue(service.authenticate(" ").isEmpty());
        assertTrue(service.authenticate("x".repeat(500)).isEmpty());
        verify(repository, never()).findSession(any(), any());
    }

    @Test
    void eachUseSlidesTheExpiry() {
        String token = "some-token";
        String hash = TokenService.hash(token);
        UUID userId = UUID.randomUUID();
        when(repository.findSession(hash, NOW))
                .thenReturn(Optional.of(new TokenSession(userId, "me@example.com", hash, NOW.plus(Duration.ofDays(3)))));

        Optional<AuthUser> user = service.authenticate(token);

        assertEquals(userId, user.orElseThrow().id());
        verify(repository).extend(hash, NOW.plus(Duration.ofDays(90)));
    }

    @Test
    void recentlyExtendedTokensAreNotWrittenAgain() {
        String token = "some-token";
        String hash = TokenService.hash(token);
        Instant almostFresh = NOW.plus(Duration.ofDays(90)).minus(Duration.ofMinutes(5));
        when(repository.findSession(hash, NOW))
                .thenReturn(Optional.of(new TokenSession(UUID.randomUUID(), "me@example.com", hash, almostFresh)));

        assertTrue(service.authenticate(token).isPresent());
        verify(repository, never()).extend(anyString(), any());
    }
}
