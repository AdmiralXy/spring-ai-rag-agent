package io.github.admiralxy.agent.registry;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public interface ChatClientsRegistry {

    /**
     * Checks if a chat client for the given model name exists.
     *
     * @param modelId model id or legacy name
     * @return {@code true} if the model is registered, otherwise {@code false}
     */
    boolean contains(String modelId);

    /**
     * Returns the {@link ChatClient} associated with the given model name.
     *
     * @param modelId model id or legacy name
     * @return chat client
     */
    ChatClient getChatClient(String modelId);

    /**
     * Returns runtime properties for the specified model.
     *
     * @param modelId model id or legacy name
     * @return configuration properties
     */
    ChatModelRuntimeProperties getProperties(String modelId);

    /**
     * Returns summarizer chat client if configured.
     *
     * @return chat client for chat title summarization
     */
    Optional<ChatClient> getSummarizerClient();

    /**
     * Returns system prompt for summarizer model.
     *
     * @return optional prompt
     */
    Optional<String> getSummarizerSystemPrompt();
}
