package io.github.admiralxy.agent.service.user;

import io.github.admiralxy.agent.config.properties.SecurityProperties;
import io.github.admiralxy.agent.entity.UserTokenEntity;
import io.github.admiralxy.agent.repository.UserTokenRepository;
import io.github.admiralxy.agent.security.AuthUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Opaque bearer tokens. Only their SHA-256 is stored, so a leaked database does not give access to accounts.
 *
 * <p>Expiry slides: every use of a token pushes its expiry back to {@code now + ttl} (at most once per
 * {@link #EXTEND_STEP}, so requests do not write to the database each time). A session therefore only ends
 * after {@code ttl} of inactivity, an explicit sign-out or a password change.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    static final Duration EXTEND_STEP = Duration.ofHours(1);

    private static final int TOKEN_BYTES = 32;
    private static final int MAX_TOKEN_LENGTH = 128;
    private static final String SHA_256 = "SHA-256";
    private static final String CLEANUP_INTERVAL = "PT6H";
    private static final String REMOVED = "Removed {} expired token(s)";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserTokenRepository repository;
    private final SecurityProperties properties;
    private final Clock clock;

    public String issue(UUID userId) {
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant now = clock.instant();
        UserTokenEntity entity = new UserTokenEntity();
        entity.setTokenHash(hash(token));
        entity.setUserId(userId);
        entity.setCreatedAt(now);
        entity.setExpiresAt(now.plus(properties.getTokenTtl()));
        repository.save(entity);
        return token;
    }

    public Optional<AuthUser> authenticate(String token) {
        if (StringUtils.isBlank(token) || token.length() > MAX_TOKEN_LENGTH) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        return repository.findSession(hash(token), now).map(session -> {
            Instant expiresAt = now.plus(properties.getTokenTtl());
            if (session.expiresAt().plus(EXTEND_STEP).isBefore(expiresAt)) {
                repository.extend(session.tokenHash(), expiresAt);
            }
            return session.user();
        });
    }

    public void revoke(String tokenHash) {
        repository.deleteByHash(tokenHash);
    }

    public void revokeOthers(UUID userId, String keepTokenHash) {
        repository.deleteOthers(userId, keepTokenHash);
    }

    @Scheduled(initialDelayString = CLEANUP_INTERVAL, fixedDelayString = CLEANUP_INTERVAL)
    public void removeExpired() {
        int removed = repository.deleteExpired(clock.instant());
        if (removed > 0) {
            log.info(REMOVED, removed);
        }
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance(SHA_256).digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
