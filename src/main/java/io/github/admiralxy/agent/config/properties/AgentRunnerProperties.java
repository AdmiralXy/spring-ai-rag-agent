package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.agent-runner")
@Getter
@Setter
public class AgentRunnerProperties {

    private static final String DEFAULT_URL = "http://localhost:8787";
    private static final int DEFAULT_MAX_TURNS = 100;

    /**
     * Base URL of the coding agent sandbox service (spring-ai-rag-agent-runner).
     */
    private String url = DEFAULT_URL;

    /**
     * Shared secret sent in the {@code x-runner-secret} header; empty disables it.
     */
    private String secret = StringUtils.EMPTY;

    /**
     * Max agent loop turns per message.
     */
    private int maxTurns = DEFAULT_MAX_TURNS;
}
