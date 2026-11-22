package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "app.files")
@Getter
@Setter
public class FilesProperties implements InitializingBean {

    private static final long REQUEST_OVERHEAD_BYTES = 1024;
    private static final long GCM_TAG_BYTES = 16;
    private static final int BASE64_GROUP = 4;
    private static final int BYTES_PER_GROUP = 3;
    private static final String DEFAULT_STORAGE_DIR = "storage/files";
    private static final String CHUNK_SIZE_INVALID = "app.files.uploads.chunk-size must be positive";
    private static final String MAX_FILES_INVALID = "app.files.uploads.max-files must be positive";
    private static final String MAX_BATCH_INVALID = "app.files.uploads.max-batch-chunks must be positive";
    private static final String MAX_REQUEST_TOO_SMALL = "app.files.uploads.max-request-size must be at least %d "
            + "bytes to fit an encrypted base64 chunk of %d bytes";
    private static final String KDF_SALT_EMPTY = "app.files.uploads.encryption.kdf-salt must not be empty";
    private static final String KDF_ITERATIONS_INVALID =
            "app.files.uploads.encryption.kdf-iterations must be at least %d";

    /**
     * Max decoded size of a file uploaded with {@code POST /files}.
     */
    private DataSize maxSize = DataSize.ofMegabytes(10);

    /**
     * Directory for file bodies stored on disk.
     */
    private String storageDir = DEFAULT_STORAGE_DIR;

    /**
     * Encrypted chunked folder uploads.
     */
    private Uploads uploads = new Uploads();

    @Override
    public void afterPropertiesSet() {
        long chunk = uploads.chunkSize.toBytes();
        require(chunk > 0, CHUNK_SIZE_INVALID);
        require(uploads.maxFiles > 0, MAX_FILES_INVALID);
        require(uploads.maxBatchChunks > 0, MAX_BATCH_INVALID);
        long minRequest = (chunk + GCM_TAG_BYTES + 2) / BYTES_PER_GROUP * BASE64_GROUP + REQUEST_OVERHEAD_BYTES;
        require(uploads.maxRequestSize.toBytes() >= minRequest, MAX_REQUEST_TOO_SMALL.formatted(minRequest, chunk));
        Encryption encryption = uploads.encryption;
        require(encryption.kdfSalt != null && !encryption.kdfSalt.isEmpty(), KDF_SALT_EMPTY);
        require(encryption.kdfIterations >= Encryption.MIN_KDF_ITERATIONS,
                KDF_ITERATIONS_INVALID.formatted(Encryption.MIN_KDF_ITERATIONS));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    @Getter
    @Setter
    public static class Uploads {

        private static final String DEFAULT_TEMP_DIR = "storage/uploads";

        /**
         * Decoded size of one chunk.
         */
        private DataSize chunkSize = DataSize.ofMegabytes(5);

        /**
         * Max total decoded size of all files of one upload.
         */
        private DataSize maxTotalSize = DataSize.ofMegabytes(500);

        /**
         * Max number of files of one upload.
         */
        private int maxFiles = 10_000;

        /**
         * Max number of chunks in one batch request.
         */
        private int maxBatchChunks = 1000;

        /**
         * Max JSON body of {@code /files/uploads/**} requests.
         */
        private DataSize maxRequestSize = DataSize.ofMegabytes(7);

        /**
         * An upload expires after this period without chunks.
         */
        private Duration ttl = Duration.ofHours(24);

        /**
         * Directory for chunks of uploads in progress.
         */
        private String tempDir = DEFAULT_TEMP_DIR;

        /**
         * Interval of the cleanup of expired uploads.
         */
        private Duration cleanupInterval = Duration.ofMinutes(15);

        /**
         * Encryption of upload requests; the password is the admin password.
         */
        private Encryption encryption = new Encryption();
    }

    @Getter
    @Setter
    public static class Encryption {

        static final int MIN_KDF_ITERATIONS = 1;

        private static final String DEFAULT_KDF_SALT = "default-kdf-salt";

        /**
         * Public PBKDF2 salt (UTF-8 string), published to clients by {@code GET /files/uploads/config}.
         */
        private String kdfSalt = DEFAULT_KDF_SALT;

        /**
         * PBKDF2-HMAC-SHA256 iterations.
         */
        private int kdfIterations = 600_000;
    }
}
