package io.github.admiralxy.agent.service.agent;

import io.github.admiralxy.agent.domain.AgentRunStats;
import io.github.admiralxy.agent.domain.ChatMessagePart;
import io.github.admiralxy.agent.domain.ChatPartType;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AgentMessageAccumulator {

    public static final String EVENT_TEXT = "text";
    public static final String EVENT_THINKING = "thinking";
    public static final String EVENT_TOOL_START = "tool_start";
    public static final String EVENT_TOOL_END = "tool_end";
    public static final String EVENT_ERROR = "error";
    public static final String EVENT_DONE = "done";

    private static final String BLOCK_SEPARATOR = "\n\n";
    private static final String EMPTY = "";
    private static final String UNKNOWN_ERROR = "Unknown error";
    private static final String DELTA = "delta";
    private static final String NEW_BLOCK = "newBlock";
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String TITLE = "title";
    private static final String INPUT = "input";
    private static final String OUTPUT = "output";
    private static final String PARENT_ID = "parentId";
    private static final String IS_ERROR = "isError";
    private static final String MESSAGE = "message";
    private static final String USAGE = "usage";
    private static final String INPUT_TOKENS = "inputTokens";
    private static final String OUTPUT_TOKENS = "outputTokens";
    private static final String CACHE_READ_TOKENS = "cacheReadTokens";
    private static final String CACHE_CREATION_TOKENS = "cacheCreationTokens";
    private static final String NUM_TURNS = "numTurns";
    private static final String DURATION_MS = "durationMs";
    private static final String COST_USD = "costUsd";
    private static final String INTERRUPTED = "interrupted";

    private final List<ChatMessagePart> parts = new ArrayList<>();
    private AgentRunStats stats;

    public synchronized void accept(String eventType, JsonNode data) {
        switch (eventType) {
            case EVENT_TEXT -> appendText(ChatPartType.TEXT, data);
            case EVENT_THINKING -> appendText(ChatPartType.THINKING, data);
            case EVENT_TOOL_START -> parts.add(ChatMessagePart.tool(
                    data.path(ID).asString(null),
                    data.path(NAME).asString(null),
                    data.path(TITLE).asString(null),
                    data.path(INPUT).asString(null),
                    data.path(PARENT_ID).isNull() ? null : data.path(PARENT_ID).asString(null)
            ));
            case EVENT_TOOL_END -> completeTool(
                    data.path(ID).asString(EMPTY),
                    data.path(OUTPUT).asString(EMPTY),
                    data.path(IS_ERROR).asBoolean(false)
            );
            case EVENT_ERROR -> addError(data.path(MESSAGE).asString(UNKNOWN_ERROR));
            case EVENT_DONE -> stats = toStats(data);
            default -> {
            }
        }
    }

    public synchronized void addError(String message) {
        parts.add(ChatMessagePart.text(ChatPartType.ERROR, message));
    }

    public synchronized AgentRunStats stats() {
        return stats;
    }

    public synchronized List<ChatMessagePart> parts() {
        return List.copyOf(parts);
    }

    public synchronized String text() {
        return parts.stream()
                .filter(p -> p.type() == ChatPartType.TEXT)
                .map(ChatMessagePart::text)
                .collect(Collectors.joining(BLOCK_SEPARATOR))
                .trim();
    }

    public synchronized boolean isEmpty() {
        return parts.isEmpty();
    }

    private void appendText(ChatPartType type, JsonNode data) {
        String delta = data.path(DELTA).asString(EMPTY);
        if (delta.isEmpty()) {
            return;
        }
        ChatMessagePart last = parts.isEmpty() ? null : parts.getLast();
        if (last != null && last.type() == type) {
            boolean newBlock = data.path(NEW_BLOCK).asBoolean(false);
            String separator = newBlock && !last.text().isEmpty() ? BLOCK_SEPARATOR : EMPTY;
            parts.set(parts.size() - 1, last.withText(last.text() + separator + delta));
        } else {
            parts.add(ChatMessagePart.text(type, delta));
        }
    }

    private void completeTool(String toolId, String output, boolean failed) {
        for (int i = parts.size() - 1; i >= 0; i--) {
            ChatMessagePart part = parts.get(i);
            if (part.type() == ChatPartType.TOOL && toolId.equals(part.toolId())) {
                parts.set(i, part.withResult(output, failed));
                return;
            }
        }
    }

    private static AgentRunStats toStats(JsonNode data) {
        JsonNode usage = data.path(USAGE);
        AgentRunStats.Usage tokens = usage.isObject()
                ? new AgentRunStats.Usage(
                        usage.path(INPUT_TOKENS).asLong(0),
                        usage.path(OUTPUT_TOKENS).asLong(0),
                        usage.path(CACHE_READ_TOKENS).asLong(0),
                        usage.path(CACHE_CREATION_TOKENS).asLong(0))
                : null;
        return new AgentRunStats(
                data.path(NUM_TURNS).asInt(0),
                data.path(DURATION_MS).asLong(0),
                data.path(COST_USD).asDouble(0),
                tokens,
                data.path(INTERRUPTED).asBoolean(false)
        );
    }
}
