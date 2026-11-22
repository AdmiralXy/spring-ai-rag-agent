package io.github.admiralxy.agent.controller.request.chat;

import java.util.List;
import java.util.UUID;

public record CreateChatRq(List<UUID> ragSpaces) {
}
