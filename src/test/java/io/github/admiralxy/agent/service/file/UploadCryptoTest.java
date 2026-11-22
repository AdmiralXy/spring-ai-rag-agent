package io.github.admiralxy.agent.service.file;

import io.github.admiralxy.agent.config.properties.AdminProperties;
import io.github.admiralxy.agent.config.properties.FilesProperties;
import io.github.admiralxy.agent.exception.InvalidFileException;
import io.github.admiralxy.agent.exception.UploadDecryptionException;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadCryptoTest {

    private static final HexFormat HEX = HexFormat.of();
    private static final byte[] SALT = new byte[UploadCrypto.KEY_SALT_LENGTH];
    private static final byte[] IV = new byte[UploadCrypto.IV_LENGTH];

    @Test
    void hkdfMatchesRfc5869TestCase1() {
        byte[] ikm = HEX.parseHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = HEX.parseHex("000102030405060708090a0b0c");
        byte[] info = HEX.parseHex("f0f1f2f3f4f5f6f7f8f9");

        byte[] okm = UploadCrypto.hkdfSha256(ikm, salt, info);

        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf", HEX.formatHex(okm));
    }

    @Test
    void pbkdf2MatchesRfc7914Vector() {
        byte[] key = UploadCrypto.pbkdf2("passwd", "salt".getBytes(StandardCharsets.UTF_8), 1);

        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc", HEX.formatHex(key));
    }

    @Test
    void pbkdf2EncodesNonAsciiPasswordAsUtf8LikeWebCrypto() throws Exception {
        String password = "пароль-ключ-ünïcødé";
        byte[] salt = "default-kdf-salt".getBytes(StandardCharsets.UTF_8);

        assertArrayEquals(referencePbkdf2(password.getBytes(StandardCharsets.UTF_8), salt, 50),
                UploadCrypto.pbkdf2(password, salt, 50));
    }

    @Test
    void roundTripAndAuthentication() {
        UploadCrypto crypto = crypto("correct-password-123");
        String aad = UploadCrypto.chunkAad(UUID.randomUUID(), 1, 2);
        byte[] plaintext = "secret content".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = crypto.encrypt(SALT, IV, plaintext, aad);

        assertEquals(plaintext.length + UploadCrypto.TAG_LENGTH, ciphertext.length);
        assertArrayEquals(plaintext, crypto.decrypt(SALT, IV, ciphertext, aad));
        assertThrows(UploadDecryptionException.class,
                () -> crypto("another-password-12").decrypt(SALT, IV, ciphertext, aad));
        assertThrows(UploadDecryptionException.class,
                () -> crypto.decrypt(SALT, IV, ciphertext, UploadCrypto.createAad()));
        byte[] otherSalt = SALT.clone();
        otherSalt[0] = 1;
        assertThrows(UploadDecryptionException.class, () -> crypto.decrypt(otherSalt, IV, ciphertext, aad));
        byte[] tampered = ciphertext.clone();
        tampered[0] ^= 1;
        assertThrows(UploadDecryptionException.class, () -> crypto.decrypt(SALT, IV, tampered, aad));
    }

    @Test
    void malformedInputIsRejected() {
        UploadCrypto crypto = crypto("correct-password-123");

        assertThrows(InvalidFileException.class, () -> crypto.decrypt(new byte[15], IV, new byte[16], "x"));
        assertThrows(InvalidFileException.class, () -> crypto.decrypt(SALT, new byte[16], new byte[16], "x"));
        assertThrows(InvalidFileException.class, () -> crypto.decrypt(SALT, IV, new byte[15], "x"));
    }

    @Test
    void shortAdminPasswordIsRejected() {
        AdminProperties admin = new AdminProperties();
        admin.setPassword("ab");

        assertThrows(IllegalStateException.class, admin::afterPropertiesSet);
    }

    private static UploadCrypto crypto(String password) {
        FilesProperties properties = new FilesProperties();
        properties.getUploads().getEncryption().setKdfIterations(10);
        AdminProperties admin = new AdminProperties();
        admin.setPassword(password);
        return new UploadCrypto(properties, admin);
    }

    private static byte[] referencePbkdf2(byte[] password, byte[] salt, int iterations) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(password, "HmacSHA256"));
        mac.update(salt);
        byte[] u = mac.doFinal(new byte[]{0, 0, 0, 1});
        byte[] result = u.clone();
        for (int i = 1; i < iterations; i++) {
            u = mac.doFinal(u);
            for (int j = 0; j < result.length; j++) {
                result[j] ^= u[j];
            }
        }
        return result;
    }
}
