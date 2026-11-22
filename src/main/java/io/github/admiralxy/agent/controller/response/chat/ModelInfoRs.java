package io.github.admiralxy.agent.controller.response.chat;

import io.github.admiralxy.agent.entity.ChatModelProvider;

public record ModelInfoRs(String label, String value, ChatModelProvider provider, boolean thinking,
                          boolean vision) {
}
