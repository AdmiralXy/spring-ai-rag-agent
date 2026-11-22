package io.github.admiralxy.agent.service.file;

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

@Slf4j
@Component
public class UploadCrypto {

    public static final int VERSION = 1;
    public static final String KDF = "PBKDF2-SHA256";
    public static final String HKDF = "HKDF-SHA256";
    public static final String CIPHER_NAME = "AES-256-GCM";
    public static final String HKDF_INFO = "envelope-v1";
    public static final int KEY_SALT_LENGTH = 16;
    public static final int IV_LENGTH = 12;
    public static final int TAG_LENGTH = 16;

    private static final String AAD_PREFIX = "envelope-v1|";
    private static final String AAD_CREATE = AAD_PREFIX + "create";
    private static final String AAD_CHUNK = AAD_PREFIX + "chunk|%s|%d|%d";
    private static final String AAD_ACTION = AAD_PREFIX + "%s|%s";
    private static final String HMAC = "HmacSHA256";
    private static final String PBKDF2 = "PBKDF2WithHmacSHA256";
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String AES = "AES";
    private static final String KEY_SALT_FIELD = "keySalt";
    private static final String IV_FIELD = "iv";
    private static final String DATA_TOO_SHORT = "Field 'data' is too short to contain an authentication tag";
    private static final String WRONG_LENGTH = "Field '%s' must be %d bytes";
    private static final String MASTER_KEY = "masterKey";
    private static final String DECRYPTION_FAILED = "AES-GCM decryption failed";
    private static final String ENCRYPTION_FAILED = "AES-GCM encryption failed";
    private static final String PBKDF2_UNAVAILABLE = "PBKDF2 is not available";
    private static final String HMAC_UNAVAILABLE = "HMAC-SHA256 is not available";
    private static final int KEY_LENGTH = 32;
    private static final int BITS_PER_BYTE = 8;
    private static final byte HKDF_BLOCK = 1;

    private final int kdfIterations;

    /**
     * The master key of a user is PBKDF2-SHA256(upload password, kdfSalt of the user, kdfIterations). The client
     * derives it when the password is set and the server only stores the result, so neither the password nor the
     * key travel with uploads.
     */
    public UploadCrypto(FilesProperties properties) {
        this.kdfIterations = properties.getUploads().getEncryption().getKdfIterations();
    }

    public static String createAad() {
        return AAD_CREATE;
    }

    public static String chunkAad(UUID uploadId, int fileIndex, int chunkIndex) {
        return AAD_CHUNK.formatted(uploadId, fileIndex, chunkIndex);
    }

    public static String actionAad(String action, UUID uploadId) {
        return AAD_ACTION.formatted(action, uploadId);
    }

    public int kdfIterations() {
        return kdfIterations;
    }

    public byte[] decrypt(byte[] masterKey, byte[] keySalt, byte[] iv, byte[] ciphertext, String aad) {
        requireLength(masterKey, KEY_LENGTH, MASTER_KEY);
        requireLengths(keySalt, iv);
        if (ciphertext.length < TAG_LENGTH) {
            throw new InvalidFileException(DATA_TOO_SHORT);
        }
        try {
            return cipher(Cipher.DECRYPT_MODE, masterKey, keySalt, iv, aad).doFinal(ciphertext);
        } catch (AEADBadTagException e) {
            throw new UploadDecryptionException();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(DECRYPTION_FAILED, e);
        }
    }

    public byte[] encrypt(byte[] masterKey, byte[] keySalt, byte[] iv, byte[] plaintext, String aad) {
        requireLength(masterKey, KEY_LENGTH, MASTER_KEY);
        requireLengths(keySalt, iv);
        try {
            return cipher(Cipher.ENCRYPT_MODE, masterKey, keySalt, iv, aad).doFinal(plaintext);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ENCRYPTION_FAILED, e);
        }
    }

    static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH * BITS_PER_BYTE);
        try {
            return SecretKeyFactory.getInstance(PBKDF2).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(PBKDF2_UNAVAILABLE, e);
        } finally {
            spec.clearPassword();
        }
    }

    static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info) {
        try {
            Mac extract = Mac.getInstance(HMAC);
            extract.init(new SecretKeySpec(salt, HMAC));
            byte[] prk = extract.doFinal(ikm);
            Mac expand = Mac.getInstance(HMAC);
            expand.init(new SecretKeySpec(prk, HMAC));
            expand.update(info);
            expand.update(HKDF_BLOCK);
            return expand.doFinal();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(HMAC_UNAVAILABLE, e);
        }
    }

    private static Cipher cipher(int mode, byte[] masterKey, byte[] keySalt, byte[] iv, String aad)
            throws GeneralSecurityException {
        byte[] sessionKey = hkdfSha256(masterKey, keySalt, HKDF_INFO.getBytes(StandardCharsets.UTF_8));
        try {
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(mode, new SecretKeySpec(sessionKey, AES), new GCMParameterSpec(TAG_LENGTH * BITS_PER_BYTE, iv));
            cipher.updateAAD(aad.getBytes(StandardCharsets.UTF_8));
            return cipher;
        } finally {
            Arrays.fill(sessionKey, (byte) 0);
        }
    }

    private static void requireLengths(byte[] keySalt, byte[] iv) {
        requireLength(keySalt, KEY_SALT_LENGTH, KEY_SALT_FIELD);
        requireLength(iv, IV_LENGTH, IV_FIELD);
    }

    private static void requireLength(byte[] value, int length, String field) {
        if (value == null || value.length != length) {
            throw new InvalidFileException(WRONG_LENGTH.formatted(field, length));
        }
    }
}
