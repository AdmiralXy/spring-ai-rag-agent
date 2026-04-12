package io.github.admiralxy.agent.registry;

public record ChatModelRuntimeProperties(
        boolean streaming,
        int maxContextTokens,
        String systemPrompt
) {
}
