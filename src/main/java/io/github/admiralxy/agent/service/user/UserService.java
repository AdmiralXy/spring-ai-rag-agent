package io.github.admiralxy.agent.service.user;

import io.github.admiralxy.agent.config.properties.SecurityProperties;
import io.github.admiralxy.agent.entity.ChatModelProvider;
import io.github.admiralxy.agent.entity.EmbeddingModelEntity;
import io.github.admiralxy.agent.entity.EmbeddingModelProvider;
import io.github.admiralxy.agent.entity.SummarizerModelEntity;
import io.github.admiralxy.agent.entity.UserEntity;
import io.github.admiralxy.agent.exception.ApiException;
import io.github.admiralxy.agent.repository.EmbeddingModelRepository;
import io.github.admiralxy.agent.repository.SummarizerModelRepository;
import io.github.admiralxy.agent.repository.UserRepository;
import io.github.admiralxy.agent.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    public static final String DEFAULT_SUMMARIZER_PROMPT = "Generate a short chat title based only on the message "
            + "below. Rules: - Use the same language as the message. - Keep it concise, 3 to 7 words. - Do not use "
            + "quotes. - Do not add punctuation at the end. - Return only the title text. Message: %s";
    public static final int UPLOAD_KEY_LENGTH = 32;

    static final int MIN_PASSWORD_LENGTH = 8;
    static final int MAX_PASSWORD_BYTES = 72;

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final int MAX_EMAIL_LENGTH = 254;
    private static final int KDF_SALT_BYTES = 16;
    private static final String DEFAULT_EMBEDDING_URL = "https://api.openai.com";
    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-3-small";
    private static final int DEFAULT_DIMENSIONS = 1536;
    private static final int DEFAULT_MAX_DOCUMENT_TOKENS = 7000;
    private static final String EMAIL_INVALID = "Enter a valid email address";
    private static final String PASSWORD_TOO_SHORT = "The password must be at least %d characters";
    private static final String PASSWORD_TOO_LONG = "The password is too long";
    private static final String EMAIL_TAKEN = "An account with this email already exists";
    private static final String REGISTRATION_DISABLED = "Registration is disabled on this server";
    private static final String BAD_CREDENTIALS = "Wrong email or password";
    private static final String WRONG_PASSWORD = "The current password is wrong";
    private static final String SAME_PASSWORD = "The new password must differ from the current one";
    private static final String USER_NOT_FOUND = "User not found";
    private static final String UPLOAD_KEY_INVALID = "The upload key must be %d bytes";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final EmbeddingModelRepository embeddingModelRepository;
    private final SummarizerModelRepository summarizerModelRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;

    /**
     * Hash compared on sign-in with an unknown email, so both cases take about the same time.
     */
    private volatile String dummyHash;

    @Transactional
    public UserEntity register(String email, String password) {
        if (!securityProperties.isRegistrationEnabled()) {
            throw ApiException.forbidden(REGISTRATION_DISABLED);
        }
        String normalized = normalizeEmail(email);
        validatePassword(password);
        if (userRepository.existsByEmail(normalized)) {
            throw ApiException.conflict(EMAIL_TAKEN);
        }
        UserEntity user = new UserEntity();
        user.setEmail(normalized);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setUploadKdfSalt(randomSalt());
        try {
            user = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            throw ApiException.conflict(EMAIL_TAKEN);
        }
        createDefaultSettings(user.getId());
        return user;
    }

    @Transactional(readOnly = true)
    public UserEntity authenticate(String email, String password) {
        String normalized = StringUtils.trimToEmpty(email).toLowerCase(Locale.ROOT);
        UserEntity user = userRepository.findByEmail(normalized).orElse(null);
        if (user == null) {
            passwordEncoder.matches(StringUtils.defaultString(password), dummyHash());
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        if (password == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw ApiException.unauthorized(BAD_CREDENTIALS);
        }
        return user;
    }

    @Transactional(readOnly = true)
    public UserEntity get(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> ApiException.unauthorized(USER_NOT_FOUND));
    }

    @Transactional
    public void changePassword(AuthUser user, String currentPassword, String newPassword) {
        UserEntity entity = get(user.id());
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, entity.getPasswordHash())) {
            throw ApiException.badRequest(WRONG_PASSWORD);
        }
        validatePassword(newPassword);
        if (newPassword.equals(currentPassword)) {
            throw ApiException.badRequest(SAME_PASSWORD);
        }
        entity.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(entity);
    }

    /**
     * Stores the key the client derived from the upload password; the password itself never reaches the server.
     */
    @Transactional
    public void setUploadKey(UUID userId, byte[] key) {
        if (key == null || key.length != UPLOAD_KEY_LENGTH) {
            throw ApiException.badRequest(UPLOAD_KEY_INVALID.formatted(UPLOAD_KEY_LENGTH));
        }
        UserEntity entity = get(userId);
        entity.setUploadKey(key.clone());
        userRepository.save(entity);
    }

    static String normalizeEmail(String email) {
        String normalized = StringUtils.trimToEmpty(email).toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_EMAIL_LENGTH || !EMAIL.matcher(normalized).matches()) {
            throw ApiException.badRequest(EMAIL_INVALID);
        }
        return normalized;
    }

    static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw ApiException.badRequest(PASSWORD_TOO_SHORT.formatted(MIN_PASSWORD_LENGTH));
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw ApiException.badRequest(PASSWORD_TOO_LONG);
        }
    }

    private void createDefaultSettings(UUID userId) {
        EmbeddingModelEntity embedding = new EmbeddingModelEntity();
        embedding.setUserId(userId);
        embedding.setProvider(EmbeddingModelProvider.OPENAI);
        embedding.setBaseUrl(DEFAULT_EMBEDDING_URL);
        embedding.setApiKey(StringUtils.EMPTY);
        embedding.setName(DEFAULT_EMBEDDING_MODEL);
        embedding.setDimensions(DEFAULT_DIMENSIONS);
        embedding.setMaxDocumentTokens(DEFAULT_MAX_DOCUMENT_TOKENS);
        embeddingModelRepository.save(embedding);

        SummarizerModelEntity summarizer = new SummarizerModelEntity();
        summarizer.setUserId(userId);
        summarizer.setProvider(ChatModelProvider.OPENAI);
        summarizer.setName(StringUtils.EMPTY);
        summarizer.setBaseUrl(StringUtils.EMPTY);
        summarizer.setApiKey(StringUtils.EMPTY);
        summarizer.setSystemPrompt(DEFAULT_SUMMARIZER_PROMPT);
        summarizerModelRepository.save(summarizer);
    }

    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = passwordEncoder.encode(UUID.randomUUID().toString());
            dummyHash = hash;
        }
        return hash;
    }

    private static String randomSalt() {
        byte[] salt = new byte[KDF_SALT_BYTES];
        RANDOM.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }
}
