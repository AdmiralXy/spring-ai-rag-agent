package io.github.admiralxy.agent.controller.response.chat;

import io.github.admiralxy.agent.domain.ChatMessage;

import java.util.List;
import java.util.UUID;

public record ChatHistoryRs(UUID chatId, List<ChatMessage> messages) {
}
