package io.github.admiralxy.agent.controller.response.auth;

import java.util.UUID;

public record UserRs(UUID id, String email, boolean uploadKeySet) {
}
