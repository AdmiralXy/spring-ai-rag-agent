package io.github.admiralxy.agent.service.chat;

import io.github.admiralxy.agent.domain.ChatAttachment;
import io.github.admiralxy.agent.domain.ChatRole;
import io.github.admiralxy.agent.entity.ChatMessageEntity;
import io.github.admiralxy.agent.service.rag.TokenizerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.session.DefaultSessionService;
import org.springframework.ai.session.EventFilter;
import org.springframework.ai.session.InMemorySessionRepository;
import org.springframework.ai.session.SessionEvent;
import org.springframework.ai.session.SessionService;

import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionMemoryTest {

    private static final UUID CHAT_ID = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final String BIG = "word ".repeat(400);

    @Mock
    private ChatTranscript transcript;

    private SessionService sessionService;
    private ChatSessionMemory memory;

    @BeforeEach
    void setUp() {
        sessionService = DefaultSessionService.builder()
                .sessionRepository(InMemorySessionRepository.builder().build())
                .build();
        memory = new ChatSessionMemory(sessionService, transcript, new TokenizerServiceImpl());
    }

    @Test
    void seedsNewSessionFromTranscriptSkippingEmptyAnswers() {
        when(transcript.messages(CHAT_ID)).thenReturn(List.of(
                message(ChatRole.USER, "Hello"),
                message(ChatRole.ASSISTANT, "Hi there"),
                message(ChatRole.USER, "Break it"),
                message(ChatRole.ASSISTANT, "")
        ));

        memory.ensureSession(CHAT_ID);

        assertEquals(List.of("Hello", "Hi there", "Break it"), texts(sessionService.getMessages(CHAT_ID.toString())));
    }

    @Test
    void doesNotSeedAnExistingSessionTwice() {
        when(transcript.messages(CHAT_ID)).thenReturn(List.of(message(ChatRole.USER, "Hello")));

        memory.ensureSession(CHAT_ID);
        memory.ensureSession(CHAT_ID);

        verify(transcript).messages(CHAT_ID);
        assertEquals(1, sessionService.getMessages(CHAT_ID.toString()).size());
    }

    @Test
    void lastMessagesKeepsTheNewestUserAndAssistantMessages() {
        emptySession();
        memory.append(CHAT_ID, new UserMessage("one"));
        memory.append(CHAT_ID, new AssistantMessage("two"));
        memory.append(CHAT_ID, new UserMessage("three"));

        assertEquals(List.of("two", "three"), texts(memory.lastMessages(CHAT_ID, 2)));
        assertEquals(3, memory.lastMessages(CHAT_ID, 0).size());
    }

    @Test
    void historyWindowIsNullWhenEverythingFits() {
        emptySession();
        memory.append(CHAT_ID, new UserMessage("one"));
        memory.append(CHAT_ID, new AssistantMessage("two"));

        assertNull(memory.historyWindow(CHAT_ID, 10_000));
    }

    @Test
    void historyWindowKeepsTheNewestTurnsThatFitStartingWithUserMessage() {
        emptySession();
        memory.append(CHAT_ID, new UserMessage(BIG));
        memory.append(CHAT_ID, new AssistantMessage(BIG));
        memory.append(CHAT_ID, new UserMessage("short question"));
        memory.append(CHAT_ID, new AssistantMessage("short answer"));

        EventFilter window = memory.historyWindow(CHAT_ID, 100);

        assertNotNull(window);
        assertEquals(List.of("short question", "short answer"), texts(sent(window)));
    }

    @Test
    void historyWindowSendsNoHistoryWhenNothingFits() {
        emptySession();
        memory.append(CHAT_ID, new UserMessage(BIG));

        EventFilter window = memory.historyWindow(CHAT_ID, 10);

        assertNotNull(window);
        assertTrue(sent(window).isEmpty());
    }

    @Test
    void historyWindowCountsImages() {
        emptySession();
        memory.append(CHAT_ID, ChatSessionMemory.userMessage("look", List.of(image())));
        memory.append(CHAT_ID, new AssistantMessage("a cat"));

        assertNotNull(memory.historyWindow(CHAT_ID, ChatSessionMemory.IMAGE_TOKENS / 2));
        assertNull(memory.historyWindow(CHAT_ID, ChatSessionMemory.IMAGE_TOKENS * 2));
    }

    @Test
    void rebuildDropsDeletedMessagesAndKeepsImages() {
        ChatMessageEntity question = message(ChatRole.USER, "what is it?");
        question.setAttachments(List.of(image()));
        when(transcript.messages(CHAT_ID))
                .thenReturn(List.of())
                .thenReturn(List.of(question, message(ChatRole.ASSISTANT, "a cat")));
        memory.ensureSession(CHAT_ID);
        memory.append(CHAT_ID, new UserMessage("deleted question"));

        memory.rebuild(CHAT_ID);

        List<Message> messages = sessionService.getMessages(CHAT_ID.toString());
        assertEquals(List.of("what is it?", "a cat"), texts(messages));
        assertEquals(1, ((UserMessage) messages.getFirst()).getMedia().size());
        assertEquals("what is it?", memory.lastUserText(CHAT_ID));
    }

    @Test
    void deleteRemovesTheSession() {
        emptySession();

        memory.delete(CHAT_ID);

        assertNull(sessionService.findById(CHAT_ID.toString()));
    }

    private void emptySession() {
        when(transcript.messages(CHAT_ID)).thenReturn(List.of());
        memory.ensureSession(CHAT_ID);
    }

    private List<Message> sent(EventFilter window) {
        return sessionService.getEvents(CHAT_ID.toString(), window.merge(EventFilter.active())).stream()
                .map(SessionEvent::getMessage)
                .toList();
    }

    private static List<String> texts(List<Message> messages) {
        return messages.stream().map(Message::getText).toList();
    }

    private static ChatMessageEntity message(ChatRole role, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setRole(role);
        entity.setContent(content);
        return entity;
    }

    private static ChatAttachment image() {
        return new ChatAttachment("image/png", Base64.getEncoder().encodeToString(new byte[]{1, 2, 3}), "a.png");
    }
}
