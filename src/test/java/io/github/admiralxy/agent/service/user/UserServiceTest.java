package io.github.admiralxy.agent.service.user;

import io.github.admiralxy.agent.config.properties.SecurityProperties;
import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.entity.SummarizerModelEntity;
import io.github.admiralxy.agent.entity.UserEntity;
import io.github.admiralxy.agent.exception.ApiException;
import io.github.admiralxy.agent.repository.EmbeddingModelRepository;
import io.github.admiralxy.agent.repository.SummarizerModelRepository;
import io.github.admiralxy.agent.repository.UserRepository;
import io.github.admiralxy.agent.security.AuthUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final EmbeddingModelRepository embeddingModelRepository = mock(EmbeddingModelRepository.class);
    private final SummarizerModelRepository summarizerModelRepository = mock(SummarizerModelRepository.class);
    private final PasswordEncoder passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final SecurityProperties securityProperties = new SecurityProperties();
    private UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(userRepository, embeddingModelRepository, summarizerModelRepository,
                passwordEncoder, securityProperties);
        when(userRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
    }

    @Test
    void registersWithNormalizedEmailHashedPasswordAndDefaultSettings() {
        UserEntity user = service.register("  Me@Example.COM ", "secret-password");

        assertEquals("me@example.com", user.getEmail());
        assertNotEquals("secret-password", user.getPasswordHash());
        assertTrue(passwordEncoder.matches("secret-password", user.getPasswordHash()));
        assertEquals(32, user.getUploadKdfSalt().length());
        ArgumentCaptor<SummarizerModelEntity> summarizer = ArgumentCaptor.forClass(SummarizerModelEntity.class);
        verify(summarizerModelRepository).save(summarizer.capture());
        assertEquals(UserService.DEFAULT_SUMMARIZER_PROMPT, summarizer.getValue().getSystemPrompt());
        assertEquals(user.getId(), summarizer.getValue().getUserId());
        ArgumentCaptor<EmbeddingModelEntity> embedding = ArgumentCaptor.forClass(EmbeddingModelEntity.class);
        verify(embeddingModelRepository).save(embedding.capture());
        assertEquals(1536, embedding.getValue().getDimensions());
    }

    @Test
    void rejectsInvalidInputAndTakenEmails() {
        assertThrows(ApiException.class, () -> service.register("not-an-email", "secret-password"));
        assertThrows(ApiException.class, () -> service.register("me@example.com", "short"));
        assertThrows(ApiException.class, () -> service.register("me@example.com", "п".repeat(40)));
        when(userRepository.existsByEmail("me@example.com")).thenReturn(true);
        ApiException taken = assertThrows(ApiException.class,
                () -> service.register("me@example.com", "secret-password"));
        assertEquals(HttpStatus.CONFLICT, taken.getStatus());
    }

    @Test
    void registrationCanBeDisabled() {
        securityProperties.setRegistrationEnabled(false);

        ApiException error = assertThrows(ApiException.class,
                () -> service.register("me@example.com", "secret-password"));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatus());
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void authenticatesOnlyWithTheRightPassword() {
        UserEntity user = user("secret-password");
        when(userRepository.findByEmail("me@example.com")).thenReturn(Optional.of(user));

        assertSame(user, service.authenticate("ME@example.com", "secret-password"));
        assertThrows(ApiException.class, () -> service.authenticate("me@example.com", "wrong-password"));
        assertThrows(ApiException.class, () -> service.authenticate("nobody@example.com", "secret-password"));
    }

    @Test
    void changesThePasswordAfterCheckingTheCurrentOne() {
        UserEntity user = user("secret-password");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        AuthUser auth = new AuthUser(user.getId(), user.getEmail(), "hash");

        assertThrows(ApiException.class, () -> service.changePassword(auth, "wrong-password", "new-password-1"));
        service.changePassword(auth, "secret-password", "new-password-1");

        assertTrue(passwordEncoder.matches("new-password-1", user.getPasswordHash()));
    }

    @Test
    void storesOnlyUploadKeysOfTheRightLength() {
        UserEntity user = user("secret-password");
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThrows(ApiException.class, () -> service.setUploadKey(user.getId(), new byte[16]));
        service.setUploadKey(user.getId(), new byte[UserService.UPLOAD_KEY_LENGTH]);

        assertEquals(UserService.UPLOAD_KEY_LENGTH, user.getUploadKey().length);
    }

    private UserEntity user(String password) {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("me@example.com");
        user.setPasswordHash(passwordEncoder.encode(password));
        return user;
    }
}
