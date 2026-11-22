package io.github.admiralxy.agent.service.agent;

import io.github.admiralxy.agent.domain.AgentRunStats;
import io.github.admiralxy.agent.domain.ChatMessagePart;
import io.github.admiralxy.agent.domain.ChatPartType;
import io.github.admiralxy.agent.entity.converter.ChatMessagePartsConverter;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentMessageAccumulatorTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    private void accept(AgentMessageAccumulator acc, String type, String json) {
        acc.accept(type, mapper.readTree(json));
    }

    @Test
    void buildsOrderedPartsFromEvents() {
        AgentMessageAccumulator acc = new AgentMessageAccumulator();
        accept(acc, "session", "{\"sessionId\":\"s1\"}");
        accept(acc, "thinking", "{\"delta\":\"Let me \",\"newBlock\":true}");
        accept(acc, "thinking", "{\"delta\":\"think\",\"newBlock\":false}");
        accept(acc, "text", "{\"delta\":\"I will \",\"newBlock\":true}");
        accept(acc, "text", "{\"delta\":\"create a file.\",\"newBlock\":false}");
        accept(acc, "tool_start", "{\"id\":\"t1\",\"name\":\"Write\",\"title\":\"Main.java\","
                + "\"input\":\"class Main {}\",\"parentId\":null}");
        accept(acc, "tool_end", "{\"id\":\"t1\",\"isError\":false,\"output\":\"File created\"}");
        accept(acc, "files_changed", "{}");
        accept(acc, "text", "{\"delta\":\"Done.\",\"newBlock\":true}");
        accept(acc, "text", "{\"delta\":\" All good\",\"newBlock\":true}");
        accept(acc, "done", "{\"numTurns\":2,\"durationMs\":1500,\"costUsd\":0.1,\"interrupted\":false,"
                + "\"usage\":{\"inputTokens\":10,\"outputTokens\":5,\"cacheReadTokens\":100,\"cacheCreationTokens\":7}}");
        assertEquals(new AgentRunStats(2, 1500, 0.1, new AgentRunStats.Usage(10, 5, 100, 7), false), acc.stats());

        List<ChatMessagePart> parts = acc.parts();
        assertEquals(4, parts.size());
        assertEquals(ChatMessagePart.text(ChatPartType.THINKING, "Let me think"), parts.get(0));
        assertEquals(ChatMessagePart.text(ChatPartType.TEXT, "I will create a file."), parts.get(1));

        ChatMessagePart tool = parts.get(2);
        assertEquals(ChatPartType.TOOL, tool.type());
        assertEquals("Write", tool.name());
        assertEquals("Main.java", tool.title());
        assertEquals("File created", tool.output());
        assertFalse(tool.isError());
        assertNull(tool.parentId());

        assertEquals("Done.\n\n All good", parts.get(3).text());
        assertEquals("I will create a file.\n\nDone.\n\n All good", acc.text());
    }

    @Test
    void keepsErrorsAndSurvivesUnknownToolResult() {
        AgentMessageAccumulator acc = new AgentMessageAccumulator();
        accept(acc, "tool_end", "{\"id\":\"missing\",\"isError\":true,\"output\":\"boom\"}");
        assertTrue(acc.isEmpty());

        accept(acc, "error", "{\"message\":\"Stopped by user\"}");
        assertEquals(List.of(ChatMessagePart.text(ChatPartType.ERROR, "Stopped by user")), acc.parts());
        assertEquals("", acc.text());
    }

    @Test
    void partsRoundTripThroughConverter() {
        ChatMessagePartsConverter converter = new ChatMessagePartsConverter();
        List<ChatMessagePart> parts = List.of(
                ChatMessagePart.text(ChatPartType.TEXT, "hello"),
                new ChatMessagePart(ChatPartType.TOOL, null, "t1", "Bash", "ls", "ls -la", "a.txt", true, "p1")
        );

        String json = converter.convertToDatabaseColumn(parts);
        assertFalse(json.contains("null"));
        assertEquals(parts, converter.convertToEntityAttribute(json));
        assertNull(converter.convertToDatabaseColumn(List.of()));
        assertNull(converter.convertToEntityAttribute(" "));
    }
}
