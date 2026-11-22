package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.outbound")
@Getter
@Setter
public class OutboundProperties {

    /**
     * Trust any TLS certificate in outgoing requests (model APIs, git, Confluence); for self-signed servers only.
     */
    private boolean insecureSsl;
}
