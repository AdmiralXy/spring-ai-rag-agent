package io.github.admiralxy.agent.config.properties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConfluenceProperties {

    /**
     * Confluence basic auth username.
     */
    private String username;

    /**
     * Confluence basic auth password.
     */
    private String password;
}
