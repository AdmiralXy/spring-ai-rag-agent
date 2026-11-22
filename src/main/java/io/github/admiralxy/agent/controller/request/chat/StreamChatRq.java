package io.github.admiralxy.agent.controller.request.chat;

import io.github.admiralxy.agent.domain.ChatAttachment;

import java.util.List;
import java.util.UUID;

public record StreamChatRq(UUID modelId, String text, String thinking, List<ChatAttachment> images) {
}
