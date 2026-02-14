package io.github.admiralxy.agent.service.impl;

import io.github.admiralxy.agent.config.properties.ChatProperties;
import io.github.admiralxy.agent.config.properties.ModelProperties;
import io.github.admiralxy.agent.config.properties.RagProperties;
import io.github.admiralxy.agent.domain.Chat;
import io.github.admiralxy.agent.domain.ChatMessage;
import io.github.admiralxy.agent.entity.ConversationEntity;
import io.github.admiralxy.agent.registry.ChatClientsRegistry;
import io.github.admiralxy.agent.repository.ConversationRepository;
import io.github.admiralxy.agent.service.RagService;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatServiceImplTest {

    private static final int PAGE_SIZE = 10;
    private static final String RAG_SPACE = "test-rag-space";
    private static final String MODEL_ALIAS = "gpt-4";
    private static final String MODEL_NAME_UPDATED = "gpt-4-turbo";
    private static final String USER_TEXT = "Hello, how are you?";
    private static final String ASSISTANT_TEXT = "I am fine, thank you!";
    private static final String CHAT_TITLE = "test-title";
    private static final String CONVERSATION_NOT_FOUND = "Conversation not found";
    private static final String SYSTEM_PROMPT = "You are helpful assistant.";
    private static final String CONTEXT_TEXT = "Some context";
    private static final String USER_MESSAGE_TYPE = "USER";
    private static final String ASSISTANT_MESSAGE_TYPE = "ASSISTANT";
    private static final String USER_CONTENT = "user message";
    private static final String ASSISTANT_CONTENT = "assistant message";
    private static final int HISTORY_LIMIT = 50;
    private static final int RAG_PERCENTAGE = 0;
    private static final int MAX_CONTEXT_TOKENS = 4000;
    private static final int TOP_K = 5;
    private static final UUID CHAT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CHAT_ID_2 = UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private RagService ragService;
    @Mock
    private ChatProperties chatProperties;
    @Mock
    private RagProperties ragProperties;
    @Mock
    private ChatClientsRegistry chatClientsRegistry;
    @Mock
    private ChatMemory chatMemory;

    @InjectMocks
    private ChatServiceImpl chatService;

    @Test
    void getAll() {
        // GIVEN
        ConversationEntity entity1 = new ConversationEntity();
        entity1.setId(CHAT_ID);
        entity1.setTitle(CHAT_TITLE);
        entity1.setModelName(MODEL_ALIAS);
        entity1.setRagSpace(RAG_SPACE);

        ConversationEntity entity2 = new ConversationEntity();
        entity2.setId(CHAT_ID_2);
        entity2.setTitle(CHAT_TITLE);
        entity2.setModelName(MODEL_ALIAS);
        entity2.setRagSpace(RAG_SPACE);

        Page<ConversationEntity> entityPage = new PageImpl<>(List.of(entity1, entity2));
        when(conversationRepository.findAll(any(Pageable.class))).thenReturn(entityPage);

        // WHEN
        Page<Chat> result = chatService.getAll(PAGE_SIZE);

        // THEN
        assertEquals(2, result.getContent().size());
        Chat firstChat = result.getContent().get(0);
        assertEquals(CHAT_ID, firstChat.id());
        assertEquals(CHAT_TITLE, firstChat.title());
        assertEquals(MODEL_ALIAS, firstChat.modelName());
        assertEquals(RAG_SPACE, firstChat.ragSpace());
        verify(conversationRepository).findAll(any(Pageable.class));
    }

    @ParameterizedTest
    @MethodSource("createDataProvider")
    void create(String ragSpace, String expectedRagSpace) {
        // GIVEN
        when(conversationRepository.save(any(ConversationEntity.class))).thenAnswer(invocation -> {
            ConversationEntity entity = invocation.getArgument(0);
            entity.setId(CHAT_ID);
            return entity;
        });

        // WHEN
        Pair<UUID, String> result = chatService.create(ragSpace);

        // THEN
        assertEquals(CHAT_ID, result.getLeft());
        assertNotNull(result.getRight());
        ArgumentCaptor<ConversationEntity> captor = ArgumentCaptor.forClass(ConversationEntity.class);
        verify(conversationRepository).save(captor.capture());
        assertEquals(expectedRagSpace, captor.getValue().getRagSpace());
    }

    private static Stream<Arguments> createDataProvider() {
        return Stream.of(
                Arguments.of(RAG_SPACE, RAG_SPACE),
                Arguments.of((String) null, null)
        );
    }

    @ParameterizedTest
    @MethodSource("updateModelNameDataProvider")
    void updateModelName(UUID chatId, boolean exists, String modelName) {
        // GIVEN
        if (exists) {
            ConversationEntity entity = new ConversationEntity();
            entity.setId(chatId);
            entity.setModelName(MODEL_ALIAS);
            when(conversationRepository.findById(chatId)).thenReturn(Optional.of(entity));
            when(conversationRepository.save(any(ConversationEntity.class))).thenAnswer(i -> i.getArgument(0));
        } else {
            when(conversationRepository.findById(chatId)).thenReturn(Optional.empty());
        }

        // WHEN & THEN
        if (exists) {
            String result = chatService.updateModelName(chatId, modelName);
            assertEquals(modelName, result);
            verify(conversationRepository).save(any(ConversationEntity.class));
        } else {
            assertThrows(RuntimeException.class, () -> chatService.updateModelName(chatId, modelName));
        }
    }

    private static Stream<Arguments> updateModelNameDataProvider() {
        return Stream.of(
                Arguments.of(CHAT_ID, true, MODEL_NAME_UPDATED),
                Arguments.of(CHAT_ID_2, false, MODEL_NAME_UPDATED)
        );
    }

    @ParameterizedTest
    @MethodSource("sendDataProvider")
    void send(UUID chatId, String modelAlias, boolean modelExists, boolean conversationExists, boolean streaming) {
        // GIVEN
        if (!modelExists) {
            when(chatClientsRegistry.contains(modelAlias)).thenReturn(false);
        } else {
            when(chatClientsRegistry.contains(modelAlias)).thenReturn(true);

            ModelProperties modelProperties = mock(ModelProperties.class);
            lenient().when(modelProperties.getMaxContextTokens()).thenReturn(MAX_CONTEXT_TOKENS);
            lenient().when(modelProperties.isStreaming()).thenReturn(streaming);
            lenient().when(modelProperties.getSystemPrompt()).thenReturn(SYSTEM_PROMPT);
            when(chatClientsRegistry.getProperties(modelAlias)).thenReturn(modelProperties);

            if (!conversationExists) {
                when(conversationRepository.findById(chatId)).thenReturn(Optional.empty());
            } else {
                ConversationEntity entity = new ConversationEntity();
                entity.setId(chatId);
                entity.setRagSpace(RAG_SPACE);
                when(conversationRepository.findById(chatId)).thenReturn(Optional.of(entity));

                when(ragProperties.getPercentage()).thenReturn(RAG_PERCENTAGE);
                when(ragProperties.getTopK()).thenReturn(TOP_K);
                when(ragService.buildContext(any(), any(), anyDouble(), anyInt(), anyInt()))
                        .thenReturn(CONTEXT_TEXT);

                ChatClient chatClient = mock(ChatClient.class);
                ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
                when(chatClient.prompt()).thenReturn(requestSpec);
                when(requestSpec.system(anyString())).thenReturn(requestSpec);
                when(requestSpec.user(USER_TEXT)).thenReturn(requestSpec);
                when(requestSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
                when(chatClientsRegistry.getChatClient(modelAlias)).thenReturn(chatClient);

                if (streaming) {
                    ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
                    when(requestSpec.stream()).thenReturn(streamSpec);
                    when(streamSpec.content()).thenReturn(Flux.just(ASSISTANT_TEXT));
                } else {
                    ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
                    when(requestSpec.call()).thenReturn(callSpec);
                    when(callSpec.content()).thenReturn(ASSISTANT_TEXT);
                }

                when(chatMemory.get(chatId.toString(), 1)).thenReturn(List.of());
            }
        }

        // WHEN & THEN
        if (!modelExists) {
            assertThrows(RuntimeException.class, () -> chatService.send(chatId, modelAlias, USER_TEXT));
        } else if (!conversationExists) {
            Flux<String> result = chatService.send(chatId, modelAlias, USER_TEXT);
            StepVerifier.create(result)
                    .expectError(IllegalArgumentException.class)
                    .verify();
        } else {
            Flux<String> result = chatService.send(chatId, modelAlias, USER_TEXT);
            StepVerifier.create(result)
                    .expectNext(ASSISTANT_TEXT)
                    .verifyComplete();
        }
    }

    private static Stream<Arguments> sendDataProvider() {
        return Stream.of(
                Arguments.of(CHAT_ID, MODEL_ALIAS, true, true, true),
                Arguments.of(CHAT_ID, MODEL_ALIAS, true, true, false),
                Arguments.of(CHAT_ID, MODEL_ALIAS, false, false, false),
                Arguments.of(CHAT_ID, MODEL_ALIAS, true, false, false)
        );
    }

    @Test
    void history() {
        // GIVEN
        when(chatProperties.getHistoryLimit()).thenReturn(HISTORY_LIMIT);
        List<Message> messages = List.of(
                new UserMessage(USER_CONTENT),
                new AssistantMessage(ASSISTANT_CONTENT)
        );
        when(chatMemory.get(CHAT_ID.toString(), HISTORY_LIMIT)).thenReturn(messages);

        // WHEN
        List<ChatMessage> result = chatService.history(CHAT_ID);

        // THEN
        assertEquals(2, result.size());
        assertEquals(USER_MESSAGE_TYPE, result.get(0).role());
        assertEquals(USER_CONTENT, result.get(0).content());
        assertEquals(ASSISTANT_MESSAGE_TYPE, result.get(1).role());
        assertEquals(ASSISTANT_CONTENT, result.get(1).content());
        verify(chatMemory).get(CHAT_ID.toString(), HISTORY_LIMIT);
    }

    @ParameterizedTest
    @MethodSource("deleteDataProvider")
    void delete(UUID chatId, boolean exists) {
        // GIVEN
        when(conversationRepository.existsById(chatId)).thenReturn(exists);

        // WHEN & THEN
        if (exists) {
            chatService.delete(chatId);
            verify(conversationRepository).deleteById(chatId);
            verify(chatMemory).clear(chatId.toString());
        } else {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> chatService.delete(chatId));
            assertEquals(CONVERSATION_NOT_FOUND, exception.getMessage());
            verify(conversationRepository, never()).deleteById(any());
            verify(chatMemory, never()).clear(any());
        }
    }

    private static Stream<Arguments> deleteDataProvider() {
        return Stream.of(
                Arguments.of(CHAT_ID, true),
                Arguments.of(CHAT_ID_2, false)
        );
    }
}
