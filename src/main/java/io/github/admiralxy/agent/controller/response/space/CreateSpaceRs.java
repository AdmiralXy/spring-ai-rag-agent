package io.github.admiralxy.agent.controller.response.space;

import java.time.Instant;
import java.util.UUID;

public record CreateSpaceRs(UUID id, String name, Instant createdAt) {
}
