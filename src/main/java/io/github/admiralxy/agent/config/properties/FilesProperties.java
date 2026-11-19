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

    /**
     * Reserve for JSON syntax, field name and data URL prefix around a base64 chunk.
     */
    private static final long REQUEST_OVERHEAD_BYTES = 1024;

    /**
     * Max decoded size of a file uploaded via POST /files.
     */
    private DataSize maxSize = DataSize.ofMegabytes(10);

    /**
     * Directory for file bodies stored on disk.
     */
    private String storageDir = "storage/files";

    /**
     * Chunked folder uploads.
     */
    private Uploads uploads = new Uploads();

    @Override
    public void afterPropertiesSet() {
        long chunk = uploads.chunkSize.toBytes();
        if (chunk <= 0) {
            throw new IllegalStateException("app.files.uploads.chunk-size must be positive");
        }
        if (uploads.maxFiles <= 0) {
            throw new IllegalStateException("app.files.uploads.max-files must be positive");
        }
        long minRequest = (chunk + 2) / 3 * 4 + REQUEST_OVERHEAD_BYTES;
        if (uploads.maxRequestSize.toBytes() < minRequest) {
            throw new IllegalStateException("app.files.uploads.max-request-size must be at least " + minRequest
                    + " bytes to fit a base64-encoded chunk of " + chunk + " bytes");
        }
    }

    @Getter
    @Setter
    public static class Uploads {

        /**
         * Size of a decoded chunk.
         */
        private DataSize chunkSize = DataSize.ofMegabytes(5);

        /**
         * Max total decoded size of all files in one session.
         */
        private DataSize maxTotalSize = DataSize.ofMegabytes(500);

        /**
         * Max number of files in one session.
         */
        private int maxFiles = 10_000;

        /**
         * Max JSON request body size for upload endpoints.
         */
        private DataSize maxRequestSize = DataSize.ofMegabytes(7);

        /**
         * Session lifetime without activity.
         */
        private Duration ttl = Duration.ofHours(24);

        /**
         * Directory for temporary chunks.
         */
        private String tempDir = "storage/uploads";

        /**
         * Interval of the expired sessions cleanup job.
         */
        private Duration cleanupInterval = Duration.ofMinutes(15);
    }
}
