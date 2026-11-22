package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.config.properties.AgentRunnerProperties;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
public class ChatErrors {

    private static final String HTTP_STATUS = "%d %s";
    private static final String RUNNER_UNAVAILABLE = "Agent runner is unavailable at %s: %s";
    private static final String RUNNER_ERROR = "Agent runner error %d: %s";
    private static final String MESSAGE_FIELD = "message";

    private final AgentRunnerProperties agentRunnerProperties;
    private final JsonMapper jsonMapper;

    public String describe(Throwable e) {
        if (e instanceof WebClientResponseException ex) {
            return HTTP_STATUS.formatted(ex.getStatusCode().value(), StringUtils.defaultString(ex.getStatusText()))
                    .trim();
        }
        return StringUtils.defaultIfBlank(e.getMessage(), e.getClass().getSimpleName());
    }

    public String describeAgent(Throwable e) {
        if (e instanceof WebClientRequestException) {
            return RUNNER_UNAVAILABLE.formatted(agentRunnerProperties.getUrl(), describe(e));
        }
        if (e instanceof WebClientResponseException ex) {
            return RUNNER_ERROR.formatted(ex.getStatusCode().value(), responseMessage(ex.getResponseBodyAsString()));
        }
        return describe(e);
    }

    private String responseMessage(String body) {
        try {
            return jsonMapper.readTree(body).path(MESSAGE_FIELD).asString(body);
        } catch (RuntimeException e) {
            return body;
        }
    }
}
