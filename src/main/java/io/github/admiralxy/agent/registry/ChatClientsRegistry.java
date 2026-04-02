package io.github.admiralxy.agent.registry;

import io.github.admiralxy.agent.config.properties.ModelProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public interface ChatClientsRegistry {

    /**
     * Checks if a chat client for the given model name exists.
     *
     * @param alias model alias
     * @return {@code true} if the model is registered, otherwise {@code false}
     */
    boolean contains(String alias);

    /**
     * Returns the {@link ChatClient} associated with the given model name.
     *
     * @param alias model alias
     * @return chat client
     */
    ChatClient getChatClient(String alias);

    /**
     * Returns the {@link ModelProperties} for the specified model.
     *
     * @param alias model alias
     * @return configuration properties
     */
    ModelProperties getProperties(String alias);

    /**
     * Returns random configured summarizer alias. If no summarizer configured, returns random available alias.
     *
     * @return alias for chat title summarization
     */
    Optional<String> getSummarizerAlias();
}
