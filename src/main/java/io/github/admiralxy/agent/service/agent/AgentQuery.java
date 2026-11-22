package io.github.admiralxy.agent.service.agent;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.admiralxy.agent.domain.ChatAttachment;

import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentQuery(
        String prompt,
        String model,
        String token,
        String systemPrompt,
        int maxTurns,
        List<Map<String, String>> history,
        String effort,
        List<ChatAttachment> images
) {
}
