package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.domain.ChatAttachment;

import java.util.List;
import java.util.UUID;

public record SendMessageCommand(UUID modelId, String text, String thinking, List<ChatAttachment> images) {
}
