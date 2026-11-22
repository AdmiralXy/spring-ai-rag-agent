package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.domain.ChatRole;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ChatEvents {

    public static final String TEXT = "text";
    public static final String THINKING = "thinking";
    public static final String ERROR = "error";
    public static final String DONE = "done";
    public static final String MESSAGE = "message";
    public static final String NOTICE = "notice";

    private static final String DELTA = "delta";
    private static final String NEW_BLOCK = "newBlock";
    private static final String MESSAGE_FIELD = "message";
    private static final String IS_ERROR = "isError";
    private static final String REJECTED = "rejected";
    private static final String ROLE = "role";
    private static final String ID = "id";
    private static final String KIND = "kind";

    private final JsonMapper jsonMapper;

    public ServerSentEvent<String> text(String delta) {
        return event(TEXT, Map.of(DELTA, delta));
    }

    public ServerSentEvent<String> thinking(String delta) {
        return event(THINKING, Map.of(DELTA, delta, NEW_BLOCK, false));
    }

    public ServerSentEvent<String> error(String message) {
        return event(ERROR, Map.of(MESSAGE_FIELD, message));
    }

    public ServerSentEvent<String> done() {
        return event(DONE, Map.of());
    }

    public ServerSentEvent<String> failed() {
        return event(DONE, Map.of(IS_ERROR, true));
    }

    public ServerSentEvent<String> rejected() {
        return event(DONE, Map.of(IS_ERROR, true, REJECTED, true));
    }

    public ServerSentEvent<String> message(ChatRole role, UUID id) {
        return event(MESSAGE, Map.of(ROLE, role.name(), ID, id.toString()));
    }

    public ServerSentEvent<String> notice(String kind, String message) {
        return event(NOTICE, Map.of(KIND, kind, MESSAGE_FIELD, message));
    }

    public ServerSentEvent<String> raw(String event, String data) {
        return ServerSentEvent.<String>builder(data).event(event).build();
    }

    public List<ServerSentEvent<String>> failure(@Nullable UUID answerId, String message) {
        List<ServerSentEvent<String>> events = new ArrayList<>(3);
        if (answerId != null) {
            events.add(message(ChatRole.ASSISTANT, answerId));
        }
        events.add(error(message));
        events.add(failed());
        return events;
    }

    private ServerSentEvent<String> event(String type, Object payload) {
        return raw(type, jsonMapper.writeValueAsString(payload));
    }
}
