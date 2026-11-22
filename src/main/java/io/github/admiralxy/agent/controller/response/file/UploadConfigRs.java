package io.github.admiralxy.agent.controller.response.file;

public record UploadConfigRs(
        long chunkSize,
        long maxTotalSize,
        int maxFiles,
        long maxRequestSize,
        int maxBatchChunks,
        UploadEncryptionRs encryption
) {
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
            int tagLength,
            boolean keyConfigured
    ) {
    }
}
