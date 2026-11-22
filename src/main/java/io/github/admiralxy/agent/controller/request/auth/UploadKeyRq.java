package io.github.admiralxy.agent.controller.request.auth;

/**
 * Base64 of PBKDF2-SHA256(upload password, the kdfSalt of the user, kdfIterations), computed by the client.
 */
public record UploadKeyRq(String key) {
}
