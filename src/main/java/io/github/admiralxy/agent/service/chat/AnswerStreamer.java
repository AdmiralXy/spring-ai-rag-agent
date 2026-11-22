package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.entity.ChatModelProvider;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

public interface AnswerStreamer {

    boolean supports(ChatModelProvider provider);

    Flux<ServerSentEvent<String>> stream(AnswerRequest request);
}
