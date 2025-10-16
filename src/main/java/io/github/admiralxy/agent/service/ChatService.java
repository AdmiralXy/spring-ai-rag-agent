package io.github.admiralxy.agent.service;

import io.github.admiralxy.agent.domain.Chat;
import io.github.admiralxy.agent.domain.ChatMessage;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;

public interface ChatService {

    /**
     * Get all chats.
     *
     * @param size page size
     * @return all chats
     */
    Page<Chat> getAll(int size);

    /**
     * Create chat with specified RAG space.
     *
     * @param ragSpace RAG space name
     * @return pair of chat ID and chat title
     */
    Pair<UUID, String> create(String ragSpace);

    /**
     * Updates the model name associated with the specified chat.
     *
     * @param chatId    unique identifier of the chat
     * @param modelName new model name to be assigned to the chat
     * @return updated name
     */
    String updateModelName(UUID chatId, String modelName);

    /**
     * Send message to chat and get response.
     *
     * @param id chat ID
     * @param modelAlias model alias
     * @param text message text
     * @return response text as a stream
     */
    Flux<String> send(UUID id, String modelAlias, String text);

    /**
     * Get chat history.
     *
     * @param id chat ID
     * @return chat history
     */
    List<ChatMessage> history(UUID id);

    /**
     * Delete chat by ID.
     *
     * @param chatId chat ID
     */
    void delete(UUID chatId);
}
