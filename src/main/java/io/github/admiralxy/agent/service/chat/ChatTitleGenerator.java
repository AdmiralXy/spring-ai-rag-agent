package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.entity.ChatEntity;
import io.github.admiralxy.agent.entity.SummarizerModelEntity;
import io.github.admiralxy.agent.repository.ChatRepository;
import io.github.admiralxy.agent.repository.SummarizerModelRepository;
import io.github.admiralxy.agent.service.model.ChatModelClientFactory;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.stereotype.Component;

import java.util.IllegalFormatException;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatTitleGenerator {

    private static final String FALLBACK_PROMPT = "Message: %s";
    private static final String PARAGRAPH = "\n\n";
    private static final String QUOTE = "\"";
    private static final int MAX_TITLE_LENGTH = 120;
    private static final String GENERATION_FAILED = "Failed to generate the title of chat {}";

    private final SummarizerModelRepository summarizerRepository;
    private final ChatModelClientFactory clientFactory;
    private final ChatRepository chatRepository;
    private final ChatTranscript transcript;

    public void generateIfFirstMessage(ChatEntity chat, String message) {
        if (chat.isTitleGenerated() || !transcript.isEmpty(chat.getId())) {
            return;
        }
        summarizerRepository.findById(SummarizerModelEntity.SINGLETON_ID)
                .filter(summarizer -> StringUtils.isNotBlank(summarizer.getApiKey()))
                .ifPresent(summarizer -> generate(chat, summarizer, message));
    }

    private void generate(ChatEntity chat, SummarizerModelEntity summarizer, String message) {
        try {
            String generated = clientFactory.summarizerClient(summarizer).prompt()
                    .user(prompt(summarizer.getSystemPrompt(), message))
                    .call()
                    .content();
            chat.setTitle(sanitize(generated, message));
            chat.setTitleGenerated(true);
            chatRepository.save(chat);
        } catch (RuntimeException e) {
            log.warn(GENERATION_FAILED, chat.getId(), e);
        }
    }

    private static String prompt(@Nullable String template, String message) {
        if (StringUtils.isBlank(template)) {
            return FALLBACK_PROMPT.formatted(message);
        }
        try {
            return template.formatted(message);
        } catch (IllegalFormatException e) {
            return template + PARAGRAPH + FALLBACK_PROMPT.formatted(message);
        }
    }

    private static String sanitize(@Nullable String generated, String fallback) {
        String title = StringUtils.defaultIfBlank(generated, fallback).trim();
        title = Strings.CS.removeEnd(Strings.CS.removeStart(title, QUOTE), QUOTE).trim();
        return StringUtils.truncate(title, MAX_TITLE_LENGTH).trim();
    }
}
