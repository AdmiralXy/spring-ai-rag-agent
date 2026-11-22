package io.github.admiralxy.agent.controller.request.auth;

public record ChangePasswordRq(String currentPassword, String newPassword) {
}
