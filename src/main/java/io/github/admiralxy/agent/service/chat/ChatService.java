package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.domain.Chat;
import io.github.admiralxy.agent.domain.ChatMessage;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ChatService {

    Page<Chat> getAll(int page, int size, @Nullable String query);

    Chat create(Collection<UUID> spaceIds);

    UUID updateModel(UUID chatId, UUID modelId);

    Flux<ServerSentEvent<String>> send(UUID chatId, SendMessageCommand command);

    boolean stop(UUID chatId);

    List<ChatMessage> history(UUID chatId);

    int deleteMessages(UUID chatId, Collection<UUID> messageIds);

    void delete(UUID chatId);
}
