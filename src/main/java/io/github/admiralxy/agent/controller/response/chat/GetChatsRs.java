package io.github.admiralxy.agent.controller.response.chat;

import io.github.admiralxy.agent.domain.Chat;
import org.springframework.data.domain.Page;

public record GetChatsRs(Page<Chat> chats) {
}
