package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.entity.ChatModelEntity;
import io.github.admiralxy.agent.entity.ChatModelProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextBudgetTest {

    @Test
    void largeWindowKeepsTheKnowledgeBaseAtItsCap() {
        ContextBudget budget = ContextBudget.of(model(ChatModelProvider.OPENAI, 900_000, 0));

        assertEquals(900_000, budget.window());
        assertEquals(16_384, budget.outputTokens());
        assertEquals(24_000, budget.ragTokens(new RagProperties()));
        assertTrue(budget.inputTokens() < 900_000 - 16_384);
    }

    @Test
    void anthropicReservesItsMaxTokens() {
        ContextBudget budget = ContextBudget.of(model(ChatModelProvider.ANTHROPIC, 200_000, 0));

        assertEquals(32_000, budget.outputTokens());
        assertEquals(200_000 - 32_000 - 10_000, budget.inputTokens());
    }

    @Test
    void configuredOutputLimitIsReservedButNeverMoreThanHalfTheWindow() {
        assertEquals(8_000, ContextBudget.of(model(ChatModelProvider.OPENAI, 128_000, 8_000)).outputTokens());
        assertEquals(16_000, ContextBudget.of(model(ChatModelProvider.OPENAI, 32_000, 64_000)).outputTokens());
    }

    @Test
    void smallWindowGivesTheKnowledgeBaseAtMostHalfOfThePrompt() {
        ContextBudget budget = ContextBudget.of(model(ChatModelProvider.OPENAI, 8_000, 0));
        RagProperties properties = new RagProperties();
        properties.setPercentage(100);

        assertEquals(budget.inputTokens() / 2, budget.ragTokens(properties));
    }

    private static ChatModelEntity model(ChatModelProvider provider, int window, int output) {
        ChatModelEntity model = new ChatModelEntity();
        model.setProvider(provider);
        model.setMaxContextTokens(window);
        model.setMaxOutputTokens(output);
        return model;
    }
}
