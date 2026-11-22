package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.entity.ChatModelEntity;
import io.github.admiralxy.agent.entity.ChatModelProvider;
import io.github.admiralxy.agent.service.model.ChatModelClientFactory;

public record ContextBudget(int window, int outputTokens, int inputTokens) {

    public static final int DEFAULT_WINDOW = 128_000;

    private static final int DEFAULT_OPENAI_OUTPUT = 16_384;
    private static final int MIN_SAFETY_MARGIN = 1024;
    private static final int SAFETY_MARGIN_DIVISOR = 20;
    private static final int OUTPUT_SHARE_DIVISOR = 4;
    private static final int PERCENT = 100;

    public static ContextBudget of(ChatModelEntity model) {
        int window = model.getMaxContextTokens() > 0 ? model.getMaxContextTokens() : DEFAULT_WINDOW;
        int output = Math.min(requestedOutput(model, window), window / 2);
        int safety = Math.max(MIN_SAFETY_MARGIN, window / SAFETY_MARGIN_DIVISOR);
        return new ContextBudget(window, output, Math.max(0, window - output - safety));
    }

    public int ragTokens(RagProperties properties) {
        return (int) Math.min(agentRagTokens(properties), inputTokens / 2);
    }

    public int agentRagTokens(RagProperties properties) {
        long byShare = (long) window * Math.clamp(properties.getPercentage(), 0, PERCENT) / PERCENT;
        return (int) Math.min(byShare, properties.getMaxTokens());
    }

    private static int requestedOutput(ChatModelEntity model, int window) {
        if (model.getMaxOutputTokens() > 0) {
            return model.getMaxOutputTokens();
        }
        if (model.getProvider() == ChatModelProvider.ANTHROPIC) {
            return ChatModelClientFactory.DEFAULT_ANTHROPIC_MAX_TOKENS;
        }
        return Math.min(DEFAULT_OPENAI_OUTPUT, window / OUTPUT_SHARE_DIVISOR);
    }
}
