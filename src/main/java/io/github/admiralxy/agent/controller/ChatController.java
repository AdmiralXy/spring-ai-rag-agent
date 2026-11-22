package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.chat.CreateChatRq;
import io.github.admiralxy.agent.controller.request.chat.StreamChatRq;
import io.github.admiralxy.agent.controller.request.chat.UpdateModelRq;
import io.github.admiralxy.agent.controller.response.chat.ChatHistoryRs;
import io.github.admiralxy.agent.controller.response.chat.CreateChatRs;
import io.github.admiralxy.agent.controller.response.chat.DeleteMessagesRs;
import io.github.admiralxy.agent.controller.response.chat.GetChatsRs;
import io.github.admiralxy.agent.controller.response.chat.StopChatRs;
import io.github.admiralxy.agent.controller.response.chat.UpdateModelRs;
import io.github.admiralxy.agent.domain.Chat;
import io.github.admiralxy.agent.security.AuthUser;
import io.github.admiralxy.agent.service.agent.AgentRunnerClient;
import io.github.admiralxy.agent.service.chat.ChatService;
import io.github.admiralxy.agent.service.chat.SendMessageCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chats")
public class ChatController {

    private static final List<String> PROXIED_HEADERS = List.of(
            HttpHeaders.CONTENT_TYPE, HttpHeaders.CONTENT_LENGTH, HttpHeaders.CONTENT_DISPOSITION
    );
    private static final String PROXY_FAILED = "Agent runner request failed";

    private final ChatService chatService;
    private final AgentRunnerClient agentRunnerClient;

    @GetMapping
    public GetChatsRs getAll(@AuthenticationPrincipal AuthUser user, @RequestParam(defaultValue = "0") int page,
                             @RequestParam(defaultValue = "10") int size,
                             @RequestParam(required = false) String q) {
        return new GetChatsRs(chatService.getAll(user.id(), page, size, q));
    }

    @PostMapping
    public CreateChatRs create(@AuthenticationPrincipal AuthUser user, @RequestBody CreateChatRq rq) {
        Chat chat = chatService.create(user.id(), rq.ragSpaces() == null ? List.of() : rq.ragSpaces());
        return new CreateChatRs(chat.id(), chat.title());
    }

    @PostMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                                                @RequestBody StreamChatRq rq) {
        return chatService.send(user.id(), id,
                new SendMessageCommand(rq.modelId(), rq.text(), rq.thinking(), rq.images()));
    }

    @PostMapping("/{id}/stop")
    public StopChatRs stop(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return new StopChatRs(chatService.stop(user.id(), id));
    }

    @GetMapping("/{id}/history")
    public ChatHistoryRs history(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        return new ChatHistoryRs(id, chatService.history(user.id(), id));
    }

    @DeleteMapping("/{id}/messages")
    public DeleteMessagesRs deleteMessages(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id, @RequestParam List<UUID> ids) {
        return new DeleteMessagesRs(chatService.deleteMessages(user.id(), id, ids));
    }

    @PatchMapping("/{id}/model")
    public UpdateModelRs updateModel(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id, @RequestBody UpdateModelRq rq) {
        return new UpdateModelRs(chatService.updateModel(user.id(), id, rq.modelId()));
    }

    @DeleteMapping("/{id}")
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        chatService.delete(user.id(), id);
    }

    @GetMapping("/{id}/files")
    public JsonNode files(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        chatService.requireOwned(user.id(), id);
        return agentRunnerClient.listFiles(id);
    }

    @GetMapping("/{id}/files/content")
    public ResponseEntity<StreamingResponseBody> fileContent(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id,
                                                              @RequestParam String path) {
        chatService.requireOwned(user.id(), id);
        return proxy(agentRunnerClient.openFile(id, path));
    }

    @GetMapping("/{id}/files/archive")
    public ResponseEntity<StreamingResponseBody> filesArchive(@AuthenticationPrincipal AuthUser user, @PathVariable UUID id) {
        chatService.requireOwned(user.id(), id);
        return proxy(agentRunnerClient.openArchive(id));
    }

    private ResponseEntity<StreamingResponseBody> proxy(ClientHttpResponse response) {
        try {
            HttpHeaders headers = new HttpHeaders();
            PROXIED_HEADERS.forEach(name -> {
                String value = response.getHeaders().getFirst(name);
                if (value != null) {
                    headers.set(name, value);
                }
            });
            StreamingResponseBody body = out -> {
                try (response; InputStream in = response.getBody()) {
                    in.transferTo(out);
                }
            };
            return ResponseEntity.status(HttpStatus.valueOf(response.getStatusCode().value())).headers(headers)
                    .body(body);
        } catch (IOException | RuntimeException e) {
            response.close();
            throw new IllegalStateException(PROXY_FAILED, e);
        }
    }
}
