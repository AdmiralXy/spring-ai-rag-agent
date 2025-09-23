package io.github.admiralxy.agent.domain;

import java.time.Instant;
import java.util.UUID;

public record Space(UUID id, String name, Instant createdAt) {
}
