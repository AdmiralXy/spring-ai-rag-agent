package io.github.admiralxy.agent.service.provider;

public record RagContentRequest(
        String text,
        boolean batch,
        RagGitOptions git,
        RagProviderAuth auth
) {
}
