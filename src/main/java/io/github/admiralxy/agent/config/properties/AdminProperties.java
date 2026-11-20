package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Admin credentials. The password is the master password of the application: it protects
 * /settings/models/** (HTTP Basic, user {@link #USERNAME}) and is the upload encryption password.
 */
@Configuration
@ConfigurationProperties(prefix = "app.security.admin")
@Getter
@Setter
public class AdminProperties implements InitializingBean {

    public static final String USERNAME = "admin";

    static final int MIN_PASSWORD_LENGTH = 3;

    /**
     * Master admin password.
     */
    private String password;

    @Override
    public void afterPropertiesSet() {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException("app.security.admin.password must be at least " + MIN_PASSWORD_LENGTH
                    + " characters (env ADMIN_PASSWORD)");
        }
    }
}
