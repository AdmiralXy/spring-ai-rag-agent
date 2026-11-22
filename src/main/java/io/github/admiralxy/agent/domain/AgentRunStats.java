package io.github.admiralxy.agent.domain;

public record AgentRunStats(int numTurns, long durationMs, double costUsd, Usage usage, boolean interrupted) {

    public record Usage(long inputTokens, long outputTokens, long cacheReadTokens, long cacheCreationTokens) {
    }
}
