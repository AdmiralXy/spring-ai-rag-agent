package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "app.security.admin")
@Getter
@Setter
public class AdminProperties implements InitializingBean {

    public static final String USERNAME = "admin";

    static final int MIN_PASSWORD_LENGTH = 3;

    private static final String PASSWORD_TOO_SHORT =
            "app.security.admin.password must be at least %d characters (env ADMIN_PASSWORD)";

    /**
     * Master password: protects the model settings (HTTP Basic, user {@code admin}) and encrypts folder uploads.
     */
    private String password;

    @Override
    public void afterPropertiesSet() {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(PASSWORD_TOO_SHORT.formatted(MIN_PASSWORD_LENGTH));
        }
    }
}
