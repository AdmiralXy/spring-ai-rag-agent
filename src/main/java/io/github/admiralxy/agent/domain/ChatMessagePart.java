package io.github.admiralxy.agent.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessagePart(
        ChatPartType type,
        String text,
        String toolId,
        String name,
        String title,
        String input,
        String output,
        Boolean isError,
        String parentId
) {
    public static ChatMessagePart text(ChatPartType type, String text) {
        return new ChatMessagePart(type, text, null, null, null, null, null, null, null);
    }

    public static ChatMessagePart tool(String toolId, String name, String title, String input, String parentId) {
        return new ChatMessagePart(ChatPartType.TOOL, null, toolId, name, title, input, null, null, parentId);
    }

    public ChatMessagePart withText(String newText) {
        return new ChatMessagePart(type, newText, toolId, name, title, input, output, isError, parentId);
    }

    public ChatMessagePart withResult(String newOutput, boolean failed) {
        return new ChatMessagePart(type, text, toolId, name, title, input, newOutput, failed, parentId);
    }
}
