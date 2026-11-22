package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@ConfigurationProperties(prefix = "app.security")
@Getter
@Setter
public class SecurityProperties {

    /**
     * How long a bearer token stays valid without being used; every request extends it again.
     */
    private Duration tokenTtl = Duration.ofDays(90);

    /**
     * New accounts can be created with {@code POST /auth/register}.
     */
    private boolean registrationEnabled = true;
}
