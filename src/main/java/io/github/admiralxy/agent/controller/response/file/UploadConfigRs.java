package io.github.admiralxy.agent.controller.response.file;

/**
 * Upload limits and public encryption parameters.
 */
public record UploadConfigRs(
        long chunkSize,
        long maxTotalSize,
        int maxFiles,
        long maxRequestSize,
        int maxBatchChunks,
        UploadEncryptionRs encryption
) {

    /**
     * @param kdfSalt  PBKDF2 salt as a UTF-8 string
     * @param hkdfInfo HKDF info as a UTF-8 string
     */
    public record UploadEncryptionRs(
            int version,
            String kdf,
            int kdfIterations,
            String kdfSalt,
            String hkdf,
            String hkdfInfo,
            String cipher,
            int keySaltLength,
            int ivLength,
            int tagLength
    ) {
    }
}
