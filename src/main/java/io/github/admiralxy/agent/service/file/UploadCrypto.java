package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.config.properties.AdminProperties;
import io.github.admiralxy.agent.config.properties.FilesProperties;
import io.github.admiralxy.agent.exception.InvalidFileException;
import io.github.admiralxy.agent.exception.UploadDecryptionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.UUID;

/**
 * Application-level encryption of upload requests.
 *
 * <p>Scheme (version 1), identical on the client (WebCrypto) and here:
 * <ol>
 *     <li>{@code master = PBKDF2-HMAC-SHA256(password UTF-8, kdfSalt UTF-8, kdfIterations, 32 bytes)};</li>
 *     <li>{@code sessionKey = HKDF-SHA256(ikm = master, salt = keySalt (16 random bytes per session),
 *     info = "envelope-v1", 32 bytes)};</li>
 *     <li>every message is {@code AES-256-GCM(sessionKey, iv = 12 random bytes, aad = context string)},
 *     the 16-byte tag is appended to the ciphertext.</li>
 * </ol>
 * The AAD binds a ciphertext to its purpose (create / chunk position / action), so ciphertexts cannot be
 * replayed elsewhere. The master key is derived once at startup, so requests do not pay for PBKDF2.
 */
@Slf4j
@Component
public class UploadCrypto {

    public static final int VERSION = 1;
    public static final String HKDF_INFO = "envelope-v1";
    public static final int KEY_SALT_LENGTH = 16;
    public static final int IV_LENGTH = 12;
    public static final int TAG_LENGTH = 16;

    private static final String AAD_PREFIX = "envelope-v1|";
    private static final String DEV_PASSWORD_PREFIX = "dev-";
    private static final String HMAC = "HmacSHA256";
    private static final int KEY_LENGTH = 32;

    private final byte[] masterKey;
    private final String kdfSalt;
    private final int kdfIterations;

    /**
     * @param adminProperties the admin password is the upload encryption password
     */
    public UploadCrypto(FilesProperties properties, AdminProperties adminProperties) {
        FilesProperties.Encryption encryption = properties.getUploads().getEncryption();
        String password = adminProperties.getPassword();
        this.kdfSalt = encryption.getKdfSalt();
        this.kdfIterations = encryption.getKdfIterations();
        this.masterKey = pbkdf2(password, kdfSalt.getBytes(StandardCharsets.UTF_8), kdfIterations);
        if (password.startsWith(DEV_PASSWORD_PREFIX)) {
            log.warn("Admin password is the development default; set ADMIN_PASSWORD in production");
        }
    }

    public static String createAad() {
        return AAD_PREFIX + "create";
    }

    public static String chunkAad(UUID uploadId, int fileIndex, int chunkIndex) {
        return AAD_PREFIX + "chunk|" + uploadId + "|" + fileIndex + "|" + chunkIndex;
    }

    public static String actionAad(String action, UUID uploadId) {
        return AAD_PREFIX + action + "|" + uploadId;
    }

    public String kdfSalt() {
        return kdfSalt;
    }

    public int kdfIterations() {
        return kdfIterations;
    }

    /**
     * Decrypts and authenticates a message.
     *
     * @throws UploadDecryptionException if the key, context or data do not match
     */
    public byte[] decrypt(byte[] keySalt, byte[] iv, byte[] ciphertext, String aad) {
        requireLength(keySalt, KEY_SALT_LENGTH, "keySalt");
        requireLength(iv, IV_LENGTH, "iv");
        if (ciphertext.length < TAG_LENGTH) {
            throw new InvalidFileException("Field 'data' is too short to contain an authentication tag");
        }
        try {
            return cipher(Cipher.DECRYPT_MODE, keySalt, iv, aad).doFinal(ciphertext);
        } catch (AEADBadTagException e) {
            throw new UploadDecryptionException();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decryption failed", e);
        }
    }

    /**
     * Encrypts a message; the server only needs it for tests and tooling, clients do the same with WebCrypto.
     */
    public byte[] encrypt(byte[] keySalt, byte[] iv, byte[] plaintext, String aad) {
        requireLength(keySalt, KEY_SALT_LENGTH, "keySalt");
        requireLength(iv, IV_LENGTH, "iv");
        try {
            return cipher(Cipher.ENCRYPT_MODE, keySalt, iv, aad).doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encryption failed", e);
        }
    }

    private Cipher cipher(int mode, byte[] keySalt, byte[] iv, String aad) throws GeneralSecurityException {
        byte[] sessionKey = hkdfSha256(masterKey, keySalt, HKDF_INFO.getBytes(StandardCharsets.UTF_8));
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, new SecretKeySpec(sessionKey, "AES"), new GCMParameterSpec(TAG_LENGTH * 8, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return cipher;
        } finally {
            Arrays.fill(sessionKey, (byte) 0);
        }
    }

    static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH * 8);
        try {
            // SunJCE encodes the password as UTF-8, same as TextEncoder in WebCrypto.
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is not available", e);
        } finally {
            spec.clearPassword();
        }
    }

    /**
     * HKDF-SHA256 (RFC 5869) producing a single 32-byte block.
     */
    static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info) {
        try {
            Mac extract = Mac.getInstance(HMAC);
            extract.init(new SecretKeySpec(salt, HMAC));
            byte[] prk = extract.doFinal(ikm);

            Mac expand = Mac.getInstance(HMAC);
            expand.init(new SecretKeySpec(prk, HMAC));
            expand.update(info);
            expand.update((byte) 1);
            return expand.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }

    private static void requireLength(byte[] value, int length, String field) {
        if (value == null || value.length != length) {
            throw new InvalidFileException("Field '" + field + "' must be " + length + " bytes");
        }
    }
}
