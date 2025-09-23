package io.github.admiralxy.agent.controller;

import io.github.admiralxy.agent.controller.request.chat.CreateChatRq;
import io.github.admiralxy.agent.controller.response.chat.ChatHistoryRs;
import io.github.admiralxy.agent.controller.response.chat.CreateChatRs;
import io.github.admiralxy.agent.controller.response.chat.GetChatsRs;
import io.github.admiralxy.agent.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/chats")
public class ChatController {

    private final ChatService chatService;

    @GetMapping
    public GetChatsRs getAll(@RequestParam(defaultValue = "10") int size) {
        return new GetChatsRs(chatService.getAll(size));
    }

    @PostMapping
    public CreateChatRs create(@RequestBody CreateChatRq rq) {
        var chat = chatService.create(rq.ragSpace());
        return new CreateChatRs(chat.getKey(), chat.getValue());
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChat(@PathVariable UUID id, @RequestParam String text) {
        return chatService.send(id, text);
    }

    @GetMapping("/{id}/history")
    public ChatHistoryRs history(@PathVariable UUID id) {
        return new ChatHistoryRs(id, chatService.history(id));
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        chatService.delete(id);
    }
}
