package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.chat")
@Getter
@Setter
public class ChatProperties {

    private static final int DEFAULT_HISTORY_LIMIT = 20;

    /**
     * Messages passed to the agent when it starts a new session. Regular models get as much history as fits
     * into their context window.
     */
    private int historyLimit = DEFAULT_HISTORY_LIMIT;
}
