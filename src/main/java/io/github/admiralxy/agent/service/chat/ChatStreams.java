package io.github.admiralxy.agent.service.chat;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatStreams {

    private static final List<String> DISCONNECT_MARKERS =
            List.of("broken pipe", "forcibly closed", "clientabortexception");

    private final Map<UUID, Sinks.One<Boolean>> active = new ConcurrentHashMap<>();

    public Flux<ServerSentEvent<String>> runDetached(UUID chatId, Flux<ServerSentEvent<String>> source) {
        Sinks.One<Boolean> stopSignal = Sinks.one();
        Sinks.One<Boolean> previous = active.put(chatId, stopSignal);
        if (previous != null) {
            previous.tryEmitValue(true);
        }
        ConnectableFlux<ServerSentEvent<String>> hot = source
                .takeUntilOther(stopSignal.asMono())
                .doFinally(ignored -> active.remove(chatId, stopSignal))
                .replay();
        hot.connect();
        return hot.onErrorResume(e -> isDisconnect(e) ? Mono.empty() : Mono.error(e));
    }

    public boolean stop(UUID chatId) {
        Sinks.One<Boolean> stopSignal = active.get(chatId);
        return stopSignal != null && stopSignal.tryEmitValue(true).isSuccess();
    }

    public boolean isActive(UUID chatId) {
        return active.containsKey(chatId);
    }

    private static boolean isDisconnect(Throwable e) {
        if (e instanceof IOException) {
            return true;
        }
        String message = StringUtils.defaultString(e.getMessage()).toLowerCase(Locale.ROOT);
        return DISCONNECT_MARKERS.stream().anyMatch(message::contains);
    }
}
