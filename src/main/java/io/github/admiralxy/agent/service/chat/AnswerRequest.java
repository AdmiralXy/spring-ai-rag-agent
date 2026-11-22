package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.domain.ChatAttachment;
import io.github.admiralxy.agent.domain.ThinkingLevel;
import io.github.admiralxy.agent.entity.ChatModelEntity;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record AnswerRequest(
        UUID userId,
        UUID chatId,
        ChatModelEntity model,
        String text,
        List<ChatAttachment> attachments,
        Collection<UUID> spaceIds,
        @Nullable String previousQuestion,
        @Nullable ThinkingLevel thinking
) {
    private static final String IMAGE_ONLY_TEXT = "(see the attached image)";

    public String prompt() {
        return StringUtils.isBlank(text) ? IMAGE_ONLY_TEXT : text;
    }
}
