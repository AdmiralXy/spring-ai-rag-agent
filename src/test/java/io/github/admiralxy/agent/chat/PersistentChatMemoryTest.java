package io.github.admiralxy.agent.chat;

import io.github.admiralxy.agent.entity.ChatMessageEntity;
import io.github.admiralxy.agent.entity.ConversationEntity;
import io.github.admiralxy.agent.repository.ChatMessageRepository;
import io.github.admiralxy.agent.repository.ConversationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PersistentChatMemoryTest {

    private static final UUID CONVERSATION_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String CONVERSATION_ID = CONVERSATION_UUID.toString();
    private static final String USER_CONTENT = "Hello";
    private static final String ASSISTANT_CONTENT = "Hi there";
    private static final String SYSTEM_CONTENT = "You are helpful";
    private static final String USER_ROLE = "USER";
    private static final String ASSISTANT_ROLE = "ASSISTANT";
    private static final String SYSTEM_ROLE = "SYSTEM";
    private static final int LAST_N = 10;

    @Mock
    private ChatMessageRepository repository;
    @Mock
    private ConversationRepository conversationRepository;

    @InjectMocks
    private PersistentChatMemory persistentChatMemory;

    @ParameterizedTest
    @MethodSource("addDataProvider")
    void add(String conversationId, List<Message> messages, int expectedSaveCount) {
        // GIVEN
        ConversationEntity conversation = new ConversationEntity();
        conversation.setId(UUID.fromString(conversationId));
        lenient().when(conversationRepository.getReferenceById(UUID.fromString(conversationId))).thenReturn(conversation);
        lenient().when(repository.save(any(ChatMessageEntity.class))).thenAnswer(i -> i.getArgument(0));

        // WHEN
        persistentChatMemory.add(conversationId, messages);

        // THEN
        ArgumentCaptor<ChatMessageEntity> captor = ArgumentCaptor.forClass(ChatMessageEntity.class);
        verify(repository, times(expectedSaveCount)).save(captor.capture());
        List<ChatMessageEntity> savedEntities = captor.getAllValues();
        assertEquals(expectedSaveCount, savedEntities.size());
        for (int i = 0; i < expectedSaveCount; i++) {
            assertEquals(conversation, savedEntities.get(i).getConversation());
            assertEquals(messages.get(i).getMessageType().name(), savedEntities.get(i).getRole());
            assertEquals(messages.get(i).getContent(), savedEntities.get(i).getContent());
        }
    }

    private static Stream<Arguments> addDataProvider() {
        return Stream.of(
                Arguments.of(CONVERSATION_ID, List.of(new UserMessage(USER_CONTENT)), 1),
                Arguments.of(CONVERSATION_ID, List.of(
                        new UserMessage(USER_CONTENT),
                        new AssistantMessage(ASSISTANT_CONTENT)
                ), 2),
                Arguments.of(CONVERSATION_ID, List.of(
                        new SystemMessage(SYSTEM_CONTENT),
                        new UserMessage(USER_CONTENT),
                        new AssistantMessage(ASSISTANT_CONTENT)
                ), 3),
                Arguments.of(CONVERSATION_ID, List.of(), 0)
        );
    }

    @ParameterizedTest
    @MethodSource("getDataProvider")
    void get(String conversationId, int lastN, List<ChatMessageEntity> storedEntities, int expectedSize,
             List<String> expectedRoles, List<String> expectedContents) {
        // GIVEN
        var page = new PageImpl<>(storedEntities);
        when(repository.findByConversationIdOrderByCreatedAtDesc(
                eq(UUID.fromString(conversationId)),
                eq(PageRequest.of(0, lastN))
        )).thenReturn(page);

        // WHEN
        List<Message> result = persistentChatMemory.get(conversationId, lastN);

        // THEN
        assertEquals(expectedSize, result.size());
        for (int i = 0; i < expectedSize; i++) {
            assertEquals(expectedRoles.get(i), result.get(i).getMessageType().name());
            assertEquals(expectedContents.get(i), result.get(i).getContent());
        }
        verify(repository).findByConversationIdOrderByCreatedAtDesc(
                eq(UUID.fromString(conversationId)),
                eq(PageRequest.of(0, lastN))
        );
    }

    private static Stream<Arguments> getDataProvider() {
        ChatMessageEntity userEntity = new ChatMessageEntity();
        userEntity.setRole(USER_ROLE);
        userEntity.setContent(USER_CONTENT);

        ChatMessageEntity assistantEntity = new ChatMessageEntity();
        assistantEntity.setRole(ASSISTANT_ROLE);
        assistantEntity.setContent(ASSISTANT_CONTENT);

        ChatMessageEntity systemEntity = new ChatMessageEntity();
        systemEntity.setRole(SYSTEM_ROLE);
        systemEntity.setContent(SYSTEM_CONTENT);

        return Stream.of(
                Arguments.of(CONVERSATION_ID, LAST_N,
                        List.of(assistantEntity, userEntity),
                        2,
                        List.of(USER_ROLE, ASSISTANT_ROLE),
                        List.of(USER_CONTENT, ASSISTANT_CONTENT)),
                Arguments.of(CONVERSATION_ID, LAST_N,
                        List.of(systemEntity),
                        1,
                        List.of(SYSTEM_ROLE),
                        List.of(SYSTEM_CONTENT)),
                Arguments.of(CONVERSATION_ID, LAST_N,
                        List.of(),
                        0,
                        List.of(),
                        List.of())
        );
    }

    @Test
    void clear() {
        // GIVEN
        doNothing().when(repository).deleteByConversationId(CONVERSATION_UUID);

        // WHEN
        persistentChatMemory.clear(CONVERSATION_ID);

        // THEN
        verify(repository).deleteByConversationId(CONVERSATION_UUID);
    }
}
